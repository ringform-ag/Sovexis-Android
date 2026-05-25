package tssbridge

import (
	"encoding/json"
	"fmt"
	"math/big"

	"github.com/bnb-chain/tss-lib/common"
	"github.com/bnb-chain/tss-lib/ecdsa/keygen"
	"github.com/bnb-chain/tss-lib/ecdsa/signing"
	"github.com/bnb-chain/tss-lib/tss"
)

// startSigningProtocol starts the signing protocol.
// Returns session state, first message to send, and error.
func startSigningProtocol(sessionID, shareID string, messageToSign []byte, localSaveDataBytes []byte) (*sessionState, []byte, error) {
	// Deserialize local share data
	var localSaveData keygen.LocalPartySaveData
	if err := json.Unmarshal(localSaveDataBytes, &localSaveData); err != nil {
		return nil, nil, fmt.Errorf("failed to unmarshal local save data: %w", err)
	}

	// Create local party ID (using shareID as identifier)
	localPartyID := tss.NewPartyID(shareID, shareID, big.NewInt(1))
	// Remote party ID (in 2-of-2, the peer ID is fixed as the other party)
	remotePartyID := tss.NewPartyID("remote", "remote", big.NewInt(2))

	// Create and sort party list
	sortedParties := tss.SortPartyIDs(tss.UnSortedPartyIDs{localPartyID, remotePartyID})

	// Create parameters
	ctx := tss.NewPeerContext(sortedParties)
	params := tss.NewParameters(tss.S256(), ctx, localPartyID, 2, 2)

	// Create channels
	outCh := make(chan tss.Message, 10)
	endCh := make(chan common.SignatureData, 1)

	// Convert message to big.Int (ECDSA signing requires the message hash)
	msgInt := new(big.Int).SetBytes(messageToSign)

	// Create signing party
	party := signing.NewLocalParty(msgInt, params, localSaveData, outCh, endCh)

	// Save session state (endCh stored as interface{})
	state := &sessionState{
		party:    party,
		outCh:    outCh,
		endCh:    wrapSigningEndCh(endCh),
		isKeygen: false,
	}
	setSession(sessionID, state)

	// Start party goroutine
	go func() {
		party.Start()
	}()

	// Wait for the first message or error
	select {
	case msg := <-outCh:
		wireBytes, err := serializeMessage(msg)
		if err != nil {
			deleteSession(sessionID)
			return nil, nil, fmt.Errorf("failed to serialize message: %w", err)
		}
		return state, wireBytes, nil

	case <-endCh:
		deleteSession(sessionID)
		return nil, nil, fmt.Errorf("signing ended unexpectedly")
	}
}

// wrapSigningEndCh wraps a chan common.SignatureData into a chan interface{}
func wrapSigningEndCh(ch chan common.SignatureData) chan interface{} {
	wrapped := make(chan interface{}, 1)
	go func() {
		for data := range ch {
			wrapped <- data
		}
		close(wrapped)
	}()
	return wrapped
}

// processSigningMessage processes a signing message.
// Returns the next message to send (nil if protocol is done) and error.
func processSigningMessage(sessionID string, msgBytes []byte) ([]byte, error) {
	state := getSession(sessionID)
	if state == nil {
		return nil, fmt.Errorf("session not found: %s", sessionID)
	}

	if state.isKeygen {
		return nil, fmt.Errorf("session is a keygen session, not signing: %s", sessionID)
	}

	// Extract wire bytes
	wireBytes, err := extractWireBytes(msgBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to extract wire bytes: %w", err)
	}

	// Parse and update message
	ok, err := state.party.UpdateFromBytes(wireBytes, state.party.(*signing.LocalParty).PartyID(), true)
	if !ok || err != nil {
		return nil, fmt.Errorf("failed to update party: %w", err)
	}

	// Check if protocol is done
	select {
	case endResult := <-state.endCh:
		// Signing complete
		signData, ok := endResult.(common.SignatureData)
		if !ok {
			return nil, fmt.Errorf("unexpected endCh type")
		}

		// Get DER-encoded signature from SignatureData
		sigBytes := signData.GetSignature()
		if len(sigBytes) == 0 {
			return nil, fmt.Errorf("signature is empty")
		}

		// Save result
		result := &SigningResult{
			Signature: sigBytes,
		}
		resultBytes, err := serializeSigningResult(result)
		if err != nil {
			return nil, fmt.Errorf("failed to serialize result: %w", err)
		}

		state.setSignature(resultBytes)
		state.markFinished()

		// Return nil to indicate protocol is done
		return nil, nil

	case msg := <-state.outCh:
		// Message to send to the peer
		wireBytes, err := serializeMessage(msg)
		if err != nil {
			return nil, fmt.Errorf("failed to serialize message: %w", err)
		}
		return wireBytes, nil

	default:
		// No message available yet (non-blocking)
		return []byte{}, nil
	}
}

// getSignatureResult retrieves the signing result.
// Should only be called after the protocol is complete.
func getSignatureResult(sessionID string) ([]byte, error) {
	state := getSession(sessionID)
	if state == nil {
		return nil, fmt.Errorf("session not found: %s", sessionID)
	}

	if state.isKeygen {
		return nil, fmt.Errorf("session is a keygen session, not signing: %s", sessionID)
	}

	if !state.isSessionFinished() {
		return nil, fmt.Errorf("signing not finished yet: %s", sessionID)
	}

	sig := state.getSignature()
	if sig == nil {
		return nil, fmt.Errorf("signature result not available: %s", sessionID)
	}

	return sig, nil
}
