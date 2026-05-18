package com.sovexis.mobile.domain.storage

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis ORAM å­˜å‚¨æ··æ·†æœåŠ¡æŽ¥å£
 *
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-09
 * å®žçŽ°çŠ¶æ€? âœ?AIå¯å®žçŽ? * äººå·¥è¡¥å……: æ€§èƒ½ä¼˜åŒ–ï¼ˆå¯é€‰ï¼‰
 *
 * åˆ†å±‚ ORAM (MegaBlocks / V-ORAM)
 * å°†ç¬”è®°è¯»å†™æ“ä½œè½¬åŒ–ä¸º"æ’å®šå¤§å°çš„æ‰¹é‡å‡è£…è¯»å?æ¨¡å¼
 * åˆæœŸå¯ä»…å®žçŽ°"è®¿é—®æ¨¡å¼æ··æ·†"ï¼ˆæ¯æ¬¡è¯»å–é™„å¸?k æ¡è™šå‡è¯»å–ï¼‰
 * ä½“ç§¯å¼€é”€ <= 30KB
 *
 * å®žçŽ°ä¼˜å…ˆçº? P0 (ç«‹å³)
 * Level 1 è™šå‡è¯»å–å¯ç«‹å³å®žçŽ°ï¼Œæ— å¤–éƒ¨ä¾èµ? */
interface OramService {

    /**
     * åˆå§‹åŒ?ORAM å­˜å‚¨æ¡?     *
     * @param bucketId å­˜å‚¨æ¡?ID
     * @param bucketSize æ¡¶å¤§å°ï¼ˆæ•°æ®å—æ•°é‡ï¼‰
     * @param blockSize æ•°æ®å—å¤§å°ï¼ˆå­—èŠ‚ï¼?     * @return Resource<Unit> åˆå§‹åŒ–ç»“æž?     */
    suspend fun initializeBucket(
        bucketId: String,
        bucketSize: Int = 64,
        blockSize: Int = 4096
    ): Resource<Unit>

    /**
     * æ··æ·†å†™å…¥
     * å°†å†™å…¥æ“ä½œéšè—åœ¨è™šå‡è¯»å–ä¸?     *
     * @param bucketId å­˜å‚¨æ¡?ID
     * @param itemId æ•°æ®é¡?ID
     * @param data æ•°æ®
     * @return Resource<Unit> å†™å…¥ç»“æžœ
     */
    suspend fun obfuscatedWrite(
        bucketId: String,
        itemId: String,
        data: ByteArray
    ): Resource<Unit>

    /**
     * æ··æ·†è¯»å–
     * å®žé™…è¯»å–ç›®æ ‡æ•°æ® + k æ¡è™šå‡è¯»å–ï¼Œé˜²æ­¢è®¿é—®æ¨¡å¼æ³„éœ²
     *
     * @param bucketId å­˜å‚¨æ¡?ID
     * @param itemId æ•°æ®é¡?ID
     * @param dummyCount é™„å¸¦è™šå‡è¯»å–æ•°é‡
     * @return Resource<ByteArray> è¯»å–ç»“æžœ
     */
    suspend fun obfuscatedRead(
        bucketId: String,
        itemId: String,
        dummyCount: Int = 3
    ): Resource<ByteArray>

    /**
     * åˆ é™¤æ•°æ®é¡¹ï¼ˆæ··æ·†åˆ é™¤ï¼?     *
     * @param bucketId å­˜å‚¨æ¡?ID
     * @param itemId æ•°æ®é¡?ID
     * @return Resource<Unit> åˆ é™¤ç»“æžœ
     */
    suspend fun obfuscatedDelete(
        bucketId: String,
        itemId: String
    ): Resource<Unit>

    /**
     * èŽ·å–å­˜å‚¨æ¡¶ç»Ÿè®¡ä¿¡æ?     *
     * @param bucketId å­˜å‚¨æ¡?ID
     * @return Resource<OramBucketStats> ç»Ÿè®¡ä¿¡æ¯
     */
    suspend fun getBucketStats(bucketId: String): Resource<OramBucketStats>
}

/**
 * ORAM å­˜å‚¨æ¡¶ç»Ÿè®¡ä¿¡æ? */
data class OramBucketStats(
    val bucketId: String,
    val totalItems: Int,
    val bucketSize: Int,
    val blockSize: Int,
    val totalAccesses: Long,
    val dummyAccesses: Long,
    val overheadBytes: Long
)
