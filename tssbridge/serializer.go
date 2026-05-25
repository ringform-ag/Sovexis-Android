package tssbridge

import (
	"encoding/json"
	"fmt"

	"github.com/bnb-chain/tss-lib/tss"
)

// TssMessage is the message structure used for serialization.
type TssMessage struct {
	From      string `json:"from"`
	To        string `json:"to"`
	Round     int    `json:"round"`
	Payload   []byte `json:"payload"`
	IsBroadcast bool `json:"is_broadcast"`
}

// serializeMessage serializes a tss.Message into a byte stream.
func serializeMessage(msg tss.Message) ([]byte, error) {
	// Use tss-lib built-in WireBytes for serialization
	wireBytes, _, err := msg.WireBytes()
	if err != nil {
		return nil, fmt.Errorf("failed to get wire bytes: %w", err)
	}

	// Get message metadata
	from := msg.GetFrom().Id
	to := ""
	if toList := msg.GetTo(); len(toList) > 0 {
		to = toList[0].Id
	}

	// Build serializable message structure
	tssMsg := TssMessage{
		From:        from,
		To:          to,
		Round:       0, // tss-lib manages rounds internally
		Payload:     wireBytes,
		IsBroadcast: msg.IsBroadcast(),
	}

	// Serialize to JSON
	return json.Marshal(tssMsg)
}

// deserializeMessage deserializes a byte stream into a parseable message.
// Note: actual parsing requires party.ParseMessage; this method only extracts wire bytes.
func deserializeMessage(msgBytes []byte) (*TssMessage, error) {
	var tssMsg TssMessage
	if err := json.Unmarshal(msgBytes, &tssMsg); err != nil {
		return nil, fmt.Errorf("failed to unmarshal message: %w", err)
	}
	return &tssMsg, nil
}

// extractWireBytes extracts wire bytes from a serialized message (for passing to party.ParseMessage).
func extractWireBytes(msgBytes []byte) ([]byte, error) {
	tssMsg, err := deserializeMessage(msgBytes)
	if err != nil {
		return nil, err
	}
	return tssMsg.Payload, nil
}

// KeygenResult holds the key generation result.
type KeygenResult struct {
	ShareID    string `json:"share_id"`
	PublicKey  []byte `json:"public_key"`
	LocalData  []byte `json:"local_data"`  // Serialized local share
	Threshold  int    `json:"threshold"`
	TotalParties int `json:"total_parties"`
}

// serializeKeygenResult serializes the key generation result.
func serializeKeygenResult(result *KeygenResult) ([]byte, error) {
	return json.Marshal(result)
}

// deserializeKeygenResult deserializes the key generation result.
func deserializeKeygenResult(data []byte) (*KeygenResult, error) {
	var result KeygenResult
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to unmarshal keygen result: %w", err)
	}
	return &result, nil
}

// SigningResult holds the signing result.
type SigningResult struct {
	Signature []byte `json:"signature"` // DER-encoded ECDSA signature
	PublicKey []byte `json:"public_key"`
}

// serializeSigningResult serializes the signing result.
func serializeSigningResult(result *SigningResult) ([]byte, error) {
	return json.Marshal(result)
}

// deserializeSigningResult deserializes the signing result.
func deserializeSigningResult(data []byte) (*SigningResult, error) {
	var result SigningResult
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to unmarshal signing result: %w", err)
	}
	return &result, nil
}
