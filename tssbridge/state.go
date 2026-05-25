package tssbridge

import (
	"sync"

	"github.com/bnb-chain/tss-lib/tss"
)

// sessionState stores the session state for a single TSS protocol.
type sessionState struct {
	mu         sync.Mutex
	party      tss.Party
	outCh      chan tss.Message
	endCh      chan interface{} // keygen: chan keygen.LocalPartySaveData; signing: chan common.SignatureData
	localData  []byte           // Serialized local share (saved after key generation)
	signature  []byte           // Signing result (DER-encoded)
	isKeygen   bool             // Whether this is a key generation session
	isFinished bool             // Whether the protocol is complete
}

var (
	sessions   = make(map[string]*sessionState)
	sessionsMu sync.RWMutex
)

// getSession retrieves the session state for the given sessionID (thread-safe).
func getSession(sessionID string) *sessionState {
	sessionsMu.RLock()
	defer sessionsMu.RUnlock()
	return sessions[sessionID]
}

// setSession stores the session state for the given sessionID (thread-safe).
func setSession(sessionID string, state *sessionState) {
	sessionsMu.Lock()
	defer sessionsMu.Unlock()
	sessions[sessionID] = state
}

// deleteSession deletes the session state for the given sessionID (thread-safe).
func deleteSession(sessionID string) {
	sessionsMu.Lock()
	defer sessionsMu.Unlock()
	if state, exists := sessions[sessionID]; exists {
		// Close channels to prevent goroutine leaks
		close(state.outCh)
		close(state.endCh)
		delete(sessions, sessionID)
	}
}

// sessionExists checks whether a session with the given sessionID exists.
func sessionExists(sessionID string) bool {
	sessionsMu.RLock()
	defer sessionsMu.RUnlock()
	_, exists := sessions[sessionID]
	return exists
}

// markFinished marks the session as completed.
func (s *sessionState) markFinished() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.isFinished = true
}

// isSessionFinished checks whether the session is completed.
func (s *sessionState) isSessionFinished() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.isFinished
}

// setLocalData sets the local share data.
func (s *sessionState) setLocalData(data []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.localData = data
}

// getLocalData retrieves the local share data.
func (s *sessionState) getLocalData() []byte {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.localData
}

// setSignature sets the signing result.
func (s *sessionState) setSignature(sig []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.signature = sig
}

// getSignature retrieves the signing result.
func (s *sessionState) getSignature() []byte {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.signature
}
