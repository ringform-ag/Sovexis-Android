package com.sovexis.tss

import tssbridge.Tssbridge

/**
 * Kotlin wrapper for gomobile compiled tss-lib AAR.
 *
 * Go functions return (T, error) → gomobile generates Java methods that
 * return T and throw Exception on error. All wrappers use try/catch.
 *
 * Go package name: tssbridge → Java class name: Tssbridge
 *
 * @author Sovexis Architecture Team
 * @since 3.0.0
 * @updated 2026-06-24 — Fixed FFI contract: gomobile (T, error) → try/catch
 */
object GoTssWrapper {

    fun startKeygen(sessionID: String, localShareID: String, remoteShareID: String): ByteArray? {
        return try { Tssbridge.startKeygen(sessionID, localShareID, remoteShareID) } catch (_: Exception) { null }
    }

    fun processKeygenMessage(sessionID: String, msgBytes: ByteArray): ByteArray? {
        return try { Tssbridge.processKeygenMessage(sessionID, msgBytes) } catch (_: Exception) { null }
    }

    fun getKeygenResult(sessionID: String): ByteArray {
        return try { Tssbridge.getKeygenResult(sessionID) } catch (e: Exception) { throw e }
    }

    fun startSigning(sessionID: String, shareID: String, messageToSign: ByteArray, localSaveDataBytes: ByteArray): ByteArray? {
        return try { Tssbridge.startSigning(sessionID, shareID, messageToSign, localSaveDataBytes) } catch (_: Exception) { null }
    }

    fun processSigningMessage(sessionID: String, msgBytes: ByteArray): ByteArray? {
        return try { Tssbridge.processSigningMessage(sessionID, msgBytes) } catch (_: Exception) { null }
    }

    fun getSignatureResult(sessionID: String): ByteArray {
        return try { Tssbridge.getSignatureResult(sessionID) } catch (e: Exception) { throw e }
    }

    fun cleanupSession(sessionID: String) {
        try { Tssbridge.cleanupSession(sessionID) } catch (_: Exception) { }
    }
}
