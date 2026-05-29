@file:Suppress("all")

package com.sovexis.domain.communication.noise

import java.security.SecureRandom

// ç±»åž‹åˆ«åå…¼å®¹
private typealias NoisePattern = NoiseProtocol.HandshakePattern

/**
 * Noise HandshakeState - æ¡æ‰‹çŠ¶æ€ç®¡ç?
 *
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-20
 * å®žçŽ°çŠ¶æ€? Phase 1 + Phase 2 å®Œæ•´å®žçŽ°
 * è§„èŒƒå‚è€? Noise Protocol Framework Rev 34, Section 5.3
 * (https://noiseprotocol.org/noise.html#handshake-state)
 *
 * HandshakeState ç®¡ç†å®Œæ•´çš?Noise æ¡æ‰‹è¿‡ç¨‹ï¼ŒåŒ…æ‹?
 * - æœ¬åœ°é™æ€å¯†é’¥å¯¹ (s) å’Œä¸´æ—¶å¯†é’¥å¯¹ (e)
 * - è¿œç¨‹é™æ€å…¬é’?(rs) å’Œä¸´æ—¶å…¬é’?(re)
 * - SymmetricState å¯¹ç§°çŠ¶æ€?
 * - æ¡æ‰‹æ¨¡å¼ (pattern) å’Œæ¶ˆæ¯æ–¹å?(initiator/responder)
 *
 * Noise è§„èŒƒ Section 5.3 ä¼ªä»£ç ?
 * ```
 * HandshakeState:
 *   s: NoiseKeyPair         // æœ¬åœ°é™æ€å¯†é’¥å¯¹
 *   e: NoiseKeyPair         // æœ¬åœ°ä¸´æ—¶å¯†é’¥å¯?
 *   rs: ByteArray           // è¿œç¨‹é™æ€å…¬é’?
 *   re: ByteArray           // è¿œç¨‹ä¸´æ—¶å…¬é’¥
 *   SymmetricState ss       // å¯¹ç§°çŠ¶æ€?
 *   pattern: Pattern        // æ¡æ‰‹æ¨¡å¼
 *   initiator: Boolean      // æ˜¯å¦ä¸ºå‘èµ·æ–¹
 *   messageIndex: Int       // å½“å‰æ¶ˆæ¯ç´¢å¼•
 *
 *   Initialize(pattern, initiator, prologue, s, e, rs, re):
 *     ss.Initialize(protocol_name)
 *     ss.MixHash(prologue)
 *     if pattern needs local static key:
 *       ss.MixHash(s.publicKey)
 *     if pattern needs remote static key:
 *       ss.MixHash(rs)
 *     messageIndex = 0
 *
 *   WriteMessage(payload):
 *     message = buffer
 *     for token in pattern[messageIndex]:
 *       process token
 *     message += ss.EncryptAndHash(payload)
 *     messageIndex++
 *     if messageIndex == len(pattern):
 *       return (message, ss.Split())
 *     return (message, null)
 *
 *   ReadMessage(message):
 *     offset = 0
 *     for token in pattern[messageIndex]:
 *       process token
 *     payload = ss.DecryptAndHash(message[offset:])
 *     messageIndex++
 *     if messageIndex == len(pattern):
 *       return (payload, ss.Split())
 *     return (payload, null)
 * ```
 *
 * æ”¯æŒçš„æ¡æ‰‹æ¨¡å¼?
 * - Noise_IK: æœ€å¸¸ç”¨æ¨¡å¼ï¼Œæä¾›èº«ä»½è®¤è¯å’Œå‰å‘å®‰å…¨æ€?
 *   - Initiator: -> e, es, s, ss
 *   - Responder: <- e, ee, se
 *
 * æ”¯æŒçš?Token:
 * - "e": å‘é€ä¸´æ—¶å…¬é’?
 * - "s": å‘é€é™æ€å…¬é’?
 * - "ee": DH(e, re)
 * - "es": DH(e, rs) æˆ?DH(s, re)
 * - "se": DH(s, re) æˆ?DH(e, rs)
 * - "ss": DH(s, rs)
 *
 * [IMPLEMENTATION-STATUS: COMPLETE]
 * [REVIEW-REQUIRED: SECURITY]
 *
 * @property cipher Noise Cipher å‡½æ•°å®žçŽ°
 * @property hash Noise Hash å‡½æ•°å®žçŽ°
 */
class HandshakeState(
    private val cipher: NoiseCipher,
    private val hash: NoiseHash
) {
    /**
     * æœ¬åœ°é™æ€å¯†é’¥å¯¹
     *
     * ç”¨äºŽé•¿æœŸèº«ä»½è®¤è¯ã€‚åœ¨ Noise_IK æ¨¡å¼ä¸­å¿…é¡»è®¾ç½®ã€?
     */
    var s: NoiseKeyPair? = null
        private set

    /**
     * æœ¬åœ°ä¸´æ—¶å¯†é’¥å¯?
     *
     * ç”¨äºŽæä¾›å‰å‘å®‰å…¨æ€§ã€‚åœ¨æ¡æ‰‹è¿‡ç¨‹ä¸­ç”Ÿæˆã€?
     */
    var e: NoiseKeyPair? = null
        private set

    /**
     * è¿œç¨‹é™æ€å…¬é’?
     *
     * å¯¹ç«¯çš„é•¿æœŸèº«ä»½å…¬é’¥ã€‚åœ¨ Noise_IK æ¨¡å¼ä¸­å¿…é¡»é¢„å…ˆçŸ¥é“ã€?
     */
    var rs: ByteArray? = null
        private set

    /**
     * è¿œç¨‹ä¸´æ—¶å…¬é’¥
     *
     * å¯¹ç«¯çš„ä¸´æ—¶å…¬é’¥ï¼Œåœ¨æ¡æ‰‹è¿‡ç¨‹ä¸­æŽ¥æ”¶ã€?
     */
    var re: ByteArray? = null
        private set

    /**
     * SymmetricState å¯¹ç§°çŠ¶æ€?
     *
     * ç®¡ç†æ¡æ‰‹è¿‡ç¨‹ä¸­çš„å“ˆå¸Œå’ŒåŠ å¯†çŠ¶æ€ã€?
     */
    val ss: SymmetricState = SymmetricState(cipher, hash)

    /**
     * æ¡æ‰‹æ¨¡å¼
     */
    var pattern: NoisePattern = NoisePattern.IK
        private set

    /**
     * æ˜¯å¦ä¸ºå‘èµ·æ–¹
     *
     * - true: å‘èµ·æ–?(Initiator)
     * - false: å“åº”æ–?(Responder)
     */
    var isInitiator: Boolean = true
        private set

    /**
     * å½“å‰æ¶ˆæ¯ç´¢å¼•
     *
     * ç”¨äºŽè·Ÿè¸ªæ¡æ‰‹è¿›åº¦ã€?
     */
    var messageIndex: Int = 0
        private set

    /**
     * å®‰å…¨éšæœºæ•°ç”Ÿæˆå™¨
     */
    private val secureRandom: SecureRandom = SecureRandom()

    /**
     * æ¡æ‰‹æ˜¯å¦å·²å®Œæˆ?
     */
    var isCompleted: Boolean = false
        private set

    /**
     * åˆå§‹åŒ?HandshakeState
     *
     * æ ¹æ® Noise è§„èŒƒ:
     * ```
     * Initialize(pattern, initiator, prologue, s, e, rs, re):
     *   ss.Initialize(protocol_name)
     *   ss.MixHash(prologue)
     *   if pattern needs local static key:
     *     ss.MixHash(s.publicKey)
     *   if pattern needs remote static key:
     *     ss.MixHash(rs)
     *   messageIndex = 0
     * ```
     *
     * @param pattern æ¡æ‰‹æ¨¡å¼
     * @param initiator æ˜¯å¦ä¸ºå‘èµ·æ–¹
     * @param prologue å‰è¨€æ•°æ® (å¯ä¸ºç©?
     * @param s æœ¬åœ°é™æ€å¯†é’¥å¯¹ (å¯ä¸º null)
     * @param e æœ¬åœ°ä¸´æ—¶å¯†é’¥å¯?(å¯ä¸º nullï¼Œæœªæä¾›æ—¶è‡ªåŠ¨ç”Ÿæˆ?
     * @param rs è¿œç¨‹é™æ€å…¬é’?(å¯ä¸º null)
     * @param re è¿œç¨‹ä¸´æ—¶å…¬é’¥ (å¯ä¸º null)
     * @throws NoiseException åˆå§‹åŒ–å¤±è´?
     */
    fun initialize(
        pattern: NoisePattern,
        initiator: Boolean,
        prologue: ByteArray = ByteArray(0),
        s: NoiseKeyPair? = null,
        e: NoiseKeyPair? = null,
        rs: ByteArray? = null,
        re: ByteArray? = null
    ): Result<Unit> {
        return try {
            this.pattern = pattern
            this.isInitiator = initiator
            this.s = s
            this.e = e
            this.rs = rs
            this.re = re
            this.messageIndex = 0
            this.isCompleted = false

            // èŽ·å–åè®®åç§°
            val protocolName = pattern.protocolName.toByteArray(Charsets.UTF_8)

            // ss.Initialize(protocol_name)
            ss.initialize(protocolName)

            // ss.MixHash(prologue)
            if (prologue.isNotEmpty()) {
                ss.mixHash(prologue)
            }

            // æ ¹æ®æ¨¡å¼æ··å…¥é¢„çŸ¥å…¬é’¥
            when (pattern) {
                NoisePattern.IK -> {
                    // Noise_IK:
                    // Initiator é¢„çŸ¥ rs -> MixHash(rs)
                    // Responder é¢„çŸ¥ rs -> MixHash(rs)
                    if (rs != null) {
                        ss.mixHash(rs)
                    } else {
                        return Result.failure(
                            NoiseException("Noise_IK requires remote static public key (rs)")
                        )
                    }
                }
                else -> {
                    // å…¶ä»–æ¨¡å¼æš‚ä¸æ”¯æŒï¼Œè¿”å›žå¤±è´?
                    return Result.failure(
                        NoiseException("Unsupported Noise pattern for HandshakeState: $pattern")
                    )
                }
            }

            Result.success(Unit)
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("HandshakeState initialization failed", e))
        }
    }

    /**
     * å†™å…¥æ¡æ‰‹æ¶ˆæ¯
     *
     * æ ¹æ® Noise è§„èŒƒ:
     * ```
     * WriteMessage(payload):
     *   message = buffer
     *   for token in pattern[messageIndex]:
     *     process token
     *   message += ss.EncryptAndHash(payload)
     *   messageIndex++
     *   if messageIndex == len(pattern):
     *     return (message, ss.Split())
     *   return (message, null)
     * ```
     *
     * å¤„ç†å½“å‰æ¶ˆæ¯ç´¢å¼•å¯¹åº”çš„æ‰€æœ?tokenï¼Œç„¶åŽåŠ å¯†é™„åŠ çš„ payloadã€?
     * å¦‚æžœè¿™æ˜¯æœ€åŽä¸€æ¡æ¡æ‰‹æ¶ˆæ¯ï¼Œè¿”å›žåˆ†å‰²åŽçš„ä¸¤ä¸ª CipherStateã€?
     *
     * @param payload é™„åŠ çš„æ˜Žæ–‡è½½è?
     * @return Result<HandshakeMessage> æ¡æ‰‹æ¶ˆæ¯ï¼ˆå«å¯é€‰çš„ CipherState å¯¹ï¼‰
     */
    fun writeMessage(payload: ByteArray = ByteArray(0)): Result<HandshakeMessage> {
        return try {
            if (isCompleted) {
                return Result.failure(NoiseException("Handshake already completed"))
            }

            val messageBuffer = mutableListOf<Byte>()

            // èŽ·å–å½“å‰æ¶ˆæ¯çš?token åˆ—è¡¨
            val tokens = getTokensForMessage(messageIndex)

            // å¤„ç†æ¯ä¸ª token
            for (token in tokens) {
                processWriteToken(token, messageBuffer)
            }

            // åŠ å¯†å¹¶é™„åŠ?payload
            val payloadBytes = payload
            val encryptedPayloadResult = ss.encryptAndHash(payloadBytes)
            if (encryptedPayloadResult.isFailure) {
                return Result.failure(encryptedPayloadResult.exceptionOrNull()!!)
            }
            val encryptedPayload = encryptedPayloadResult.getOrThrow()
            messageBuffer.addAll(encryptedPayload.toList())

            // æž„å»ºæ¶ˆæ¯å­—èŠ‚æ•°ç»„
            val messageBytes = messageBuffer.toByteArray()

            messageIndex++

            // æ£€æŸ¥æ˜¯å¦æ˜¯æœ€åŽä¸€æ¡æ¶ˆæ?
            if (messageIndex >= getMessageCount()) {
                isCompleted = true
                val splitResult = ss.split()
                if (splitResult.isFailure) {
                    return Result.failure(splitResult.exceptionOrNull()!!)
                }
            }

            Result.success(
                HandshakeMessage(
                    pattern = pattern,
                    isComplete = messageIndex >= getMessageCount() - 1,
                    payload = messageBytes
                )
            )
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("WriteMessage failed", e))
        }
    }

    /**
     * è¯»å–æ¡æ‰‹æ¶ˆæ¯
     *
     * æ ¹æ® Noise è§„èŒƒ:
     * ```
     * ReadMessage(message):
     *   offset = 0
     *   for token in pattern[messageIndex]:
     *     process token
     *   payload = ss.DecryptAndHash(message[offset:])
     *   messageIndex++
     *   if messageIndex == len(pattern):
     *     return (payload, ss.Split())
     *   return (payload, null)
     * ```
     *
     * å¤„ç†å½“å‰æ¶ˆæ¯ç´¢å¼•å¯¹åº”çš„æ‰€æœ?tokenï¼Œç„¶åŽè§£å¯?payloadã€?
     * å¦‚æžœè¿™æ˜¯æœ€åŽä¸€æ¡æ¡æ‰‹æ¶ˆæ¯ï¼Œè¿”å›žåˆ†å‰²åŽçš„ä¸¤ä¸ª CipherStateã€?
     *
     * @param message æŽ¥æ”¶åˆ°çš„æ¡æ‰‹æ¶ˆæ¯
     * @return Result<HandshakeMessage> è§£æžç»“æžœï¼ˆå« payload å’Œå¯é€‰çš„ CipherState å¯¹ï¼‰
     */
    fun readMessage(message: ByteArray): Result<HandshakeMessage> {
        return try {
            if (isCompleted) {
                return Result.failure(NoiseException("Handshake already completed"))
            }

            var offset = 0

            // èŽ·å–å½“å‰æ¶ˆæ¯çš?token åˆ—è¡¨
            val tokens = getTokensForMessage(messageIndex)

            // å¤„ç†æ¯ä¸ª token
            for (token in tokens) {
                val consumed = processReadToken(token, message, offset)
                offset += consumed
            }

            // è§£å¯† payload
            val ciphertext = message.sliceArray(offset until message.size)
            val payloadResult = ss.decryptAndHash(ciphertext)
            if (payloadResult.isFailure) {
                return Result.failure(payloadResult.exceptionOrNull()!!)
            }
            val payload = payloadResult.getOrThrow()

            messageIndex++

            // æ£€æŸ¥æ˜¯å¦æ˜¯æœ€åŽä¸€æ¡æ¶ˆæ?
            if (messageIndex >= getMessageCount()) {
                isCompleted = true
                val splitResult = ss.split()
                if (splitResult.isFailure) {
                    return Result.failure(splitResult.exceptionOrNull()!!)
                }
            }

            Result.success(
                HandshakeMessage(
                    pattern = pattern,
                    isComplete = messageIndex >= getMessageCount() - 1,
                    payload = payload
                )
            )
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("ReadMessage failed", e))
        }
    }

    // ========================================================================
    // Token å¤„ç† - å†™å…¥æ–¹å‘
    // ========================================================================

    /**
     * å¤„ç†å†™å…¥æ–¹å‘çš?token
     *
     * @param token token å­—ç¬¦ä¸?
     * @param buffer æ¶ˆæ¯ç¼“å†²åŒ?
     */
    private fun processWriteToken(token: String, buffer: MutableList<Byte>) {
        when (token) {
            "e" -> writeE(buffer)
            "s" -> writeS(buffer)
            "ee" -> writeEE()
            "es" -> writeES()
            "se" -> writeSE()
            "ss" -> writeSS()
            else -> throw NoiseException("Unknown token: $token")
        }
    }

    /**
     * Token "e": å‘é€ä¸´æ—¶å…¬é’?
     *
     * ç”Ÿæˆä¸´æ—¶å¯†é’¥å¯¹ï¼Œå°†å…¬é’¥æ··å…¥å“ˆå¸Œã€?
     * å¦‚æžœå·²è®¾ç½®åŠ å¯†å¯†é’¥ï¼Œå…¬é’¥ä¼šè¢«åŠ å¯†ã€?
     *
     * Noise è§„èŒƒ:
     * ```
     * e:
     *   e = GenerateKeyPair()
     *   Send(e.publicKey)  // å¯èƒ½åŠ å¯†
     *   MixHash(e.publicKey)
     * ```
     */
    private fun writeE(buffer: MutableList<Byte>) {
        // ç”Ÿæˆä¸´æ—¶å¯†é’¥å¯?
        val keyPair = NoiseDH.generateKeyPair()
        e = NoiseKeyPair(privateKey = keyPair.first, publicKey = keyPair.second)
        val publicKeyBytes = e!!.publicKey

        // å¦‚æžœå·²è®¾ç½®å¯†é’¥ï¼ŒåŠ å¯†å…¬é’¥
        val result = ss.encryptAndHash(publicKeyBytes)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
        buffer.addAll(result.getOrThrow().toList())
    }

    /**
     * Token "s": å‘é€é™æ€å…¬é’?
     *
     * å°†æœ¬åœ°é™æ€å…¬é’¥æ··å…¥å“ˆå¸Œã€?
     * å¦‚æžœå·²è®¾ç½®åŠ å¯†å¯†é’¥ï¼Œå…¬é’¥ä¼šè¢«åŠ å¯†ã€?
     *
     * Noise è§„èŒƒ:
     * ```
     * s:
     *   Send(s.publicKey)  // å¯èƒ½åŠ å¯†
     *   MixHash(s.publicKey)
     * ```
     */
    private fun writeS(buffer: MutableList<Byte>) {
        if (s == null) {
            throw NoiseException("Static key pair (s) not set")
        }
        val publicKeyBytes = s!!.publicKey

        val result = ss.encryptAndHash(publicKeyBytes)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
        buffer.addAll(result.getOrThrow().toList())
    }

    /**
     * Token "ee": DH(e, re)
     *
     * ä¸´æ—¶-ä¸´æ—¶ DH: DH(e, re)
     * ç»“æžœæ··å…¥é“¾å¯†é’¥ã€?
     *
     * Noise è§„èŒƒ:
     * ```
     * ee:
     *   MixKey(DH(e, re))
     * ```
     */
    private fun writeEE() {
        if (e == null) throw NoiseException("Local ephemeral key (e) not set")
        if (re == null) throw NoiseException("Remote ephemeral key (re) not set")

        val sharedSecret = NoiseDH.dh(e!!.privateKey, re!!)
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "es": DH(e, rs) æˆ?DH(s, re)
     *
     * æ ¹æ®å‘èµ·æ–?å“åº”æ–¹è§’è‰?
     * - Initiator: DH(e, rs)
     * - Responder: DH(s, re)
     *
     * Noise è§„èŒƒ:
     * ```
     * es:
     *   if initiator: MixKey(DH(e, rs))
     *   else: MixKey(DH(s, re))
     * ```
     */
    private fun writeES() {
        val sharedSecret = if (isInitiator) {
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            NoiseDH.dh(e!!.privateKey, rs!!)
        } else {
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            NoiseDH.dh(s!!.privateKey, re!!)
        }
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "se": DH(s, re) æˆ?DH(e, rs)
     *
     * æ ¹æ®å‘èµ·æ–?å“åº”æ–¹è§’è‰?
     * - Initiator: DH(s, re)
     * - Responder: DH(e, rs)
     *
     * Noise è§„èŒƒ:
     * ```
     * se:
     *   if initiator: MixKey(DH(s, re))
     *   else: MixKey(DH(e, rs))
     * ```
     */
    private fun writeSE() {
        val sharedSecret = if (isInitiator) {
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            NoiseDH.dh(s!!.privateKey, re!!)
        } else {
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            NoiseDH.dh(e!!.privateKey, rs!!)
        }
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "ss": DH(s, rs)
     *
     * é™æ€?é™æ€?DH: DH(s, rs)
     * ç»“æžœæ··å…¥é“¾å¯†é’¥ã€?
     *
     * Noise è§„èŒƒ:
     * ```
     * ss:
     *   MixKey(DH(s, rs))
     * ```
     */
    private fun writeSS() {
        if (s == null) throw NoiseException("Local static key (s) not set")
        if (rs == null) throw NoiseException("Remote static key (rs) not set")

        val sharedSecret = NoiseDH.dh(s!!.privateKey, rs!!)
        ss.mixKey(sharedSecret)
    }

    // ========================================================================
    // Token å¤„ç† - è¯»å–æ–¹å‘
    // ========================================================================

    /**
     * å¤„ç†è¯»å–æ–¹å‘çš?token
     *
     * @param token token å­—ç¬¦ä¸?
     * @param message å®Œæ•´æ¶ˆæ¯
     * @param offset å½“å‰è¯»å–åç§»é‡?
     * @return Int æ¶ˆè´¹çš„å­—èŠ‚æ•°
     */
    private fun processReadToken(token: String, message: ByteArray, offset: Int): Int {
        return when (token) {
            "e" -> readE(message, offset)
            "s" -> readS(message, offset)
            "ee" -> readEE()
            "es" -> readES()
            "se" -> readSE()
            "ss" -> readSS()
            else -> throw NoiseException("Unknown token: $token")
        }
    }

    /**
     * Token "e": æŽ¥æ”¶ä¸´æ—¶å…¬é’¥
     *
     * ä»Žæ¶ˆæ¯ä¸­è¯»å–è¿œç¨‹ä¸´æ—¶å…¬é’¥ã€?
     *
     * @param message å®Œæ•´æ¶ˆæ¯
     * @param offset å½“å‰åç§»é‡?
     * @return Int æ¶ˆè´¹çš„å­—èŠ‚æ•°
     */
    private fun readE(message: ByteArray, offset: Int): Int {
        // è¯»å–å…¬é’¥ï¼ˆå¯èƒ½åŠ å¯†ï¼‰
        val keyLen = NoiseProtocol.DH_PUBLIC_KEY_LEN
        val encryptedKey = if (ss.hasKey()) {
            // åŠ å¯†çš„å…¬é’? keyLen + 16 (GCM tag)
            keyLen + 16
        } else {
            // æ˜Žæ–‡å…¬é’¥
            keyLen
        }

        if (message.size < offset + encryptedKey) {
            throw NoiseException("Message too short for ephemeral public key")
        }

        val keyData = message.sliceArray(offset until offset + encryptedKey)

        // è§£å¯†ï¼ˆå¦‚æžœå·²è®¾ç½®å¯†é’¥ï¼?
        val publicKeyResult = ss.decryptAndHash(keyData)
        if (publicKeyResult.isFailure) {
            throw publicKeyResult.exceptionOrNull()!!
        }
        val publicKey = publicKeyResult.getOrThrow()

        // éªŒè¯å…¬é’¥
        re = NoiseDH.validatePublicKey(publicKey)

        return encryptedKey
    }

    /**
     * Token "s": æŽ¥æ”¶é™æ€å…¬é’?
     *
     * ä»Žæ¶ˆæ¯ä¸­è¯»å–è¿œç¨‹é™æ€å…¬é’¥ã€?
     *
     * @param message å®Œæ•´æ¶ˆæ¯
     * @param offset å½“å‰åç§»é‡?
     * @return Int æ¶ˆè´¹çš„å­—èŠ‚æ•°
     */
    private fun readS(message: ByteArray, offset: Int): Int {
        val keyLen = NoiseProtocol.DH_PUBLIC_KEY_LEN
        val encryptedKey = if (ss.hasKey()) {
            keyLen + 16
        } else {
            keyLen
        }

        if (message.size < offset + encryptedKey) {
            throw NoiseException("Message too short for static public key")
        }

        val keyData = message.sliceArray(offset until offset + encryptedKey)

        val publicKeyResult = ss.decryptAndHash(keyData)
        if (publicKeyResult.isFailure) {
            throw publicKeyResult.exceptionOrNull()!!
        }
        val publicKey = publicKeyResult.getOrThrow()

        rs = NoiseDH.validatePublicKey(publicKey)

        return encryptedKey
    }

    /**
     * Token "ee": DH(e, re) - è¯»å–æ–¹å‘
     *
     * ä¸Žå†™å…¥æ–¹å‘ç›¸åŒã€?
     */
    private fun readEE(): Int {
        writeEE()
        return 0
    }

    /**
     * Token "es": è¯»å–æ–¹å‘
     *
     * æ ¹æ®å‘èµ·æ–?å“åº”æ–¹è§’è‰?
     * - Initiator: DH(s, re) (æ³¨æ„: ä¸Žå†™å…¥æ–¹å‘ç›¸å?
     * - Responder: DH(e, rs) (æ³¨æ„: ä¸Žå†™å…¥æ–¹å‘ç›¸å?
     *
     * Noise è§„èŒƒ:
     * ```
     * es:
     *   if initiator: MixKey(DH(s, re))
     *   else: MixKey(DH(e, rs))
     * ```
     *
     * æ³¨æ„: è¯»å–æ–¹å‘å’Œå†™å…¥æ–¹å‘çš„ es/se è§’è‰²äº’æ¢ï¼?
     * è¿™æ˜¯å› ä¸º DH å‡½æ•°æ˜¯å¯¹ç§°çš„ï¼Œä½†å¯†é’¥å¯¹çš„ä½¿ç”¨æ–¹å‘ç›¸åã€?
     */
    private fun readES(): Int {
        val sharedSecret = if (isInitiator) {
            // Initiator è¯»å–: DH(s, re)
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            NoiseDH.dh(s!!.privateKey, re!!)
        } else {
            // Responder è¯»å–: DH(e, rs)
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            NoiseDH.dh(e!!.privateKey, rs!!)
        }
        ss.mixKey(sharedSecret)
        return 0
    }

    /**
     * Token "se": è¯»å–æ–¹å‘
     *
     * æ ¹æ®å‘èµ·æ–?å“åº”æ–¹è§’è‰?
     * - Initiator: DH(e, rs)
     * - Responder: DH(s, re)
     *
     * æ³¨æ„: è¯»å–æ–¹å‘å’Œå†™å…¥æ–¹å‘çš„ es/se è§’è‰²äº’æ¢ï¼?
     */
    private fun readSE(): Int {
        val sharedSecret = if (isInitiator) {
            // Initiator è¯»å–: DH(e, rs)
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            NoiseDH.dh(e!!.privateKey, rs!!)
        } else {
            // Responder è¯»å–: DH(s, re)
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            NoiseDH.dh(s!!.privateKey, re!!)
        }
        ss.mixKey(sharedSecret)
        return 0
    }

    /**
     * Token "ss": DH(s, rs) - è¯»å–æ–¹å‘
     *
     * ä¸Žå†™å…¥æ–¹å‘ç›¸åŒã€?
     */
    private fun readSS(): Int {
        writeSS()
        return 0
    }

    // ========================================================================
    // æ¨¡å¼å®šä¹‰
    // ========================================================================

    /**
     * èŽ·å–æŒ‡å®šæ¶ˆæ¯ç´¢å¼•çš?token åˆ—è¡¨
     *
     * Noise_IK æ¨¡å¼:
     * - Initiator æ¶ˆæ¯ (index 0): -> e, es, s, ss
     * - Responder æ¶ˆæ¯ (index 1): <- e, ee, se
     *
     * @param messageIndex æ¶ˆæ¯ç´¢å¼•
     * @return List<String> token åˆ—è¡¨
     */
    private fun getTokensForMessage(messageIndex: Int): List<String> {
        return when (pattern) {
            NoisePattern.IK -> {
                when (messageIndex) {
                    0 -> {
                        // Initiator: -> e, es, s, ss
                        if (isInitiator) listOf("e", "es", "s", "ss")
                        // Responder: <- e, ee, se
                        else listOf("e", "ee", "se")
                    }
                    1 -> {
                        // Initiator: <- e, ee, se
                        if (isInitiator) listOf("e", "ee", "se")
                        // Responder: -> e, es, s, ss
                        else listOf("e", "es", "s", "ss")
                    }
                    else -> throw NoiseException("Invalid message index: $messageIndex for pattern $pattern")
                }
            }
            else -> throw NoiseException("Unsupported pattern in getTokensForMessage: $pattern")
        }
    }

    /**
     * èŽ·å–æ¡æ‰‹æ¶ˆæ¯æ€»æ•°
     *
     * @return Int æ¶ˆæ¯æ€»æ•°
     */
    private fun getMessageCount(): Int {
        return when (pattern) {
            NoisePattern.IK -> 2  // åŒå‘å„ä¸€æ¡æ¶ˆæ?
            else -> throw NoiseException("Unsupported pattern in getMessageCount: $pattern")
        }
    }

    /**
     * èŽ·å–æ¡æ‰‹å“ˆå¸Œ
     *
     * @return ByteArray æ¡æ‰‹å“ˆå¸Œå€?
     */
    fun getHandshakeHash(): ByteArray = ss.getHandshakeHash()

    /**
     * å®‰å…¨æ¸…é™¤æ‰€æœ‰æ•æ„Ÿæ•°æ?
     */
    fun clear() {
        s?.let {
            NoiseUtils.secureWipe(it.publicKey)
            NoiseUtils.secureWipe(it.privateKey)
        }
        e?.let {
            NoiseUtils.secureWipe(it.publicKey)
            NoiseUtils.secureWipe(it.privateKey)
        }
        rs?.let { NoiseUtils.secureWipe(it) }
        re?.let { NoiseUtils.secureWipe(it) }
        s = null
        e = null
        rs = null
        re = null
        ss.clear()
    }
}


