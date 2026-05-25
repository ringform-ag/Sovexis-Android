package com.sovexis.tss

import tssbridge.Tssbridge

/**
 * Kotlin wrapper for gomobile compiled tss-lib AAR.
 *
 * All methods are static calls, parameters and return values are ByteArray or String.
 * Go package name: tssbridge → Java class name: Tssbridge
 *
 * @author Sovexis Architecture Team
 * @since 3.0.0
 * @updated 2026-05-22 - Replaced mock implementation with real AAR calls
 */
object GoTssWrapper {

    /**
     * Starts the key generation protocol and returns the first message to send to the peer.
     *
     * @param sessionID unique session identifier
     * @param localShareID local share ID
     * @param remoteShareID remote share ID
     * @return first message to send (JSON-serialized TssMessage), or null on error
     */
    fun startKeygen(sessionID: String, localShareID: String, remoteShareID: String): ByteArray? {
        return Tssbridge.startKeygen(sessionID, localShareID, remoteShareID)
    }

    /**
     * Processes a key generation message from the peer and returns the next message to send, or null if the protocol is done.
     *
     * @param sessionID unique session identifier
     * @param msgBytes message from the peer (JSON-serialized TssMessage)
     * @return next message to send, null means the protocol has ended
     */
    fun processKeygenMessage(sessionID: String, msgBytes: ByteArray): ByteArray? {
        return Tssbridge.processKeygenMessage(sessionID, msgBytes)
    }

    /**
     * Retrieves the local share data after key generation is complete.
     *
     * @param sessionID unique session identifier
     * @return key generation result (JSON-serialized KeygenResult)
     */
    fun getKeygenResult(sessionID: String): ByteArray {
        return Tssbridge.getKeygenResult(sessionID)
    }

    /**
     * Starts the signing protocol and returns the first message to send.
     *
     * @param sessionID unique session identifier
     * @param shareID local share ID
     * @param messageToSign message to sign (32-byte SHA-256 hash)
     * @param localSaveDataBytes local share data (LocalData obtained from getKeygenResult)
     * @return first message to send (JSON-serialized TssMessage), or null on error
     */
    fun startSigning(sessionID: String, shareID: String, messageToSign: ByteArray, localSaveDataBytes: ByteArray): ByteArray? {
        return Tssbridge.startSigning(sessionID, shareID, messageToSign, localSaveDataBytes)
    }

    /**
     * Processes a signing message from the peer and returns the next message to send, or null.
     *
     * @param sessionID unique session identifier
     * @param msgBytes message from the peer (JSON-serialized TssMessage)
     * @return next message to send, null means the protocol has ended
     */
    fun processSigningMessage(sessionID: String, msgBytes: ByteArray): ByteArray? {
        return Tssbridge.processSigningMessage(sessionID, msgBytes)
    }

    /**
     * Retrieves the complete ECDSA signature (DER-encoded) after signing is complete.
     *
     * @param sessionID unique session identifier
     * @return signing result (JSON-serialized SigningResult)
     */
    fun getSignatureResult(sessionID: String): ByteArray {
        return Tssbridge.getSignatureResult(sessionID)
    }

    /**
     * Cleans up all state for the specified session and releases memory.
     *
     * @param sessionID unique session identifier
     */
    fun cleanupSession(sessionID: String) {
        Tssbridge.cleanupSession(sessionID)
    }
}
