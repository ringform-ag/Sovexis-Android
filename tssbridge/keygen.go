package tssbridge

import (
	"crypto/elliptic"
	"encoding/json"
	"fmt"
	"math/big"

	"github.com/bnb-chain/tss-lib/ecdsa/keygen"
	"github.com/bnb-chain/tss-lib/tss"
)

// startKeygenProtocol starts the key generation protocol.
// Returns session state, first message to send, and error.
func startKeygenProtocol(sessionID, localShareID, remoteShareID string) (*sessionState, []byte, error) {
	// Create local party ID
	localPartyID := tss.NewPartyID(localShareID, localShareID, big.NewInt(1))
	remotePartyID := tss.NewPartyID(remoteShareID, remoteShareID, big.NewInt(2))

	// Create and sort party list
	sortedParties := tss.SortPartyIDs(tss.UnSortedPartyIDs{localPartyID, remotePartyID})

	// Create parameters: secp256k1 curve, 2-of-2 threshold
	ctx := tss.NewPeerContext(sortedParties)
	params := tss.NewParameters(tss.S256(), ctx, localPartyID, 2, 2)

	// Create channels
	outCh := make(chan tss.Message, 10)
	endCh := make(chan keygen.LocalPartySaveData, 1)

	// Start keygen party (no pre-parameters)
	party := keygen.NewLocalParty(params, outCh, endCh)

	// Save session state (endCh stored as interface{})
	state := &sessionState{
		party:    party,
		outCh:    outCh,
		endCh:    wrapKeygenEndCh(endCh),
		isKeygen: true,
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
		return nil, nil, fmt.Errorf("keygen ended unexpectedly")
	}
}

// wrapKeygenEndCh wraps a chan keygen.LocalPartySaveData into a chan interface{}
func wrapKeygenEndCh(ch chan keygen.LocalPartySaveData) chan interface{} {
	wrapped := make(chan interface{}, 1)
	go func() {
		for data := range ch {
			wrapped <- data
		}
		close(wrapped)
	}()
	return wrapped
}

// processKeygenMessage processes a key generation message.
// Returns the next message to send (nil if protocol is done) and error.
func processKeygenMessage(sessionID string, msgBytes []byte) ([]byte, error) {
	state := getSession(sessionID)
	if state == nil {
		return nil, fmt.Errorf("session not found: %s", sessionID)
	}

	if !state.isKeygen {
		return nil, fmt.Errorf("session is not a keygen session: %s", sessionID)
	}

	// Extract wire bytes
	wireBytes, err := extractWireBytes(msgBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to extract wire bytes: %w", err)
	}

	// Parse and update message
	ok, err := state.party.UpdateFromBytes(wireBytes, state.party.(*keygen.LocalParty).PartyID(), true)
	if !ok || err != nil {
		return nil, fmt.Errorf("failed to update party: %w", err)
	}

	// Check if protocol is done
	select {
	case endResult := <-state.endCh:
		// Key generation complete
		keygenResult, ok := endResult.(keygen.LocalPartySaveData)
		if !ok {
			return nil, fmt.Errorf("unexpected endCh type")
		}
		// Get local share data from LocalPartySaveData
		localDataBytes, err := json.Marshal(keygenResult)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal local data: %w", err)
		}

		// Get public key
		ecPubKey := keygenResult.ECDSAPub.ToECDSAPubKey()

		// Serialize public key
		pubKeyBytes := elliptic.Marshal(ecPubKey.Curve, ecPubKey.X, ecPubKey.Y)

		// Save result
		kgResult := &KeygenResult{
			ShareID:      sessionID,
			PublicKey:    pubKeyBytes,
			LocalData:    localDataBytes,
			Threshold:    2,
			TotalParties: 2,
		}
		resultBytes, err := serializeKeygenResult(kgResult)
		if err != nil {
			return nil, fmt.Errorf("failed to serialize result: %w", err)
		}

		state.setLocalData(resultBytes)
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

// getKeygenResult retrieves the key generation result.
// Should only be called after the protocol is complete.
func getKeygenResult(sessionID string) ([]byte, error) {
	state := getSession(sessionID)
	if state == nil {
		return nil, fmt.Errorf("session not found: %s", sessionID)
	}

	if !state.isKeygen {
		return nil, fmt.Errorf("session is not a keygen session: %s", sessionID)
	}

	if !state.isSessionFinished() {
		return nil, fmt.Errorf("keygen not finished yet: %s", sessionID)
	}

	data := state.getLocalData()
	if data == nil {
		return nil, fmt.Errorf("keygen result not available: %s", sessionID)
	}

	return data, nil
}
