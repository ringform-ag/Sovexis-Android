package tssbridge

// StartKeygen starts the key generation protocol and returns the first message to be sent to the peer.
// Parameters:
//   - sessionID: unique session identifier
//   - localShareID: local share ID
//   - remoteShareID: remote share ID
// Returns:
//   - []byte: first message to send (JSON-serialized TssMessage)
//   - error: error information
func StartKeygen(sessionID string, localShareID string, remoteShareID string) ([]byte, error) {
	_, msg, err := startKeygenProtocol(sessionID, localShareID, remoteShareID)
	return msg, err
}

// ProcessKeygenMessage processes a key generation message from the peer and returns the next message to send, or nil if the protocol is done.
// Parameters:
//   - sessionID: unique session identifier
//   - msgBytes: message from the peer (JSON-serialized TssMessage)
// Returns:
//   - []byte: next message to send, nil means the protocol has ended
//   - error: error information
func ProcessKeygenMessage(sessionID string, msgBytes []byte) ([]byte, error) {
	return processKeygenMessage(sessionID, msgBytes)
}

// GetKeygenResult retrieves the local share data after key generation is complete.
// Parameters:
//   - sessionID: unique session identifier
// Returns:
//   - []byte: key generation result (JSON-serialized KeygenResult)
//   - error: error information
func GetKeygenResult(sessionID string) ([]byte, error) {
	return getKeygenResult(sessionID)
}

// StartSigning starts the signing protocol and returns the first message to send.
// Parameters:
//   - sessionID: unique session identifier
//   - shareID: local share ID
//   - messageToSign: message to sign (32-byte SHA-256 hash)
//   - localSaveDataBytes: local share data (LocalData obtained from GetKeygenResult)
// Returns:
//   - []byte: first message to send (JSON-serialized TssMessage)
//   - error: error information
func StartSigning(sessionID string, shareID string, messageToSign []byte, localSaveDataBytes []byte) ([]byte, error) {
	_, msg, err := startSigningProtocol(sessionID, shareID, messageToSign, localSaveDataBytes)
	return msg, err
}

// ProcessSigningMessage processes a signing message from the peer and returns the next message to send, or nil.
// Parameters:
//   - sessionID: unique session identifier
//   - msgBytes: message from the peer (JSON-serialized TssMessage)
// Returns:
//   - []byte: next message to send, nil means the protocol has ended
//   - error: error information
func ProcessSigningMessage(sessionID string, msgBytes []byte) ([]byte, error) {
	return processSigningMessage(sessionID, msgBytes)
}

// GetSignatureResult retrieves the complete ECDSA signature (DER-encoded) after signing is complete.
// Parameters:
//   - sessionID: unique session identifier
// Returns:
//   - []byte: signing result (JSON-serialized SigningResult)
//   - error: error information
func GetSignatureResult(sessionID string) ([]byte, error) {
	return getSignatureResult(sessionID)
}

// CleanupSession cleans up all state for the specified session and releases memory.
// Parameters:
//   - sessionID: unique session identifier
// Returns:
//   - error: error information
func CleanupSession(sessionID string) error {
	deleteSession(sessionID)
	return nil
}
