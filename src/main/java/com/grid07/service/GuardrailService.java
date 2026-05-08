package com.grid07.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Implements all three Redis-backed atomic guardrails for bot interactions:
 *
 *  1. Horizontal Cap  – max 100 bot replies per post  (post:{id}:bot_count)
 *  2. Vertical Cap    – max depth 20 in a thread
 *  3. Cooldown Cap    – 1 interaction per bot-human pair per 10 min
 *                       (cooldown:bot_{botId}:human_{humanId})
 *
 * Thread-safety: The horizontal cap uses a Lua script executed atomically
 * inside Redis, so no two concurrent requests can both read a value of 99
 * and both increment to 100 — only one succeeds and the other is rejected.
 */
@Service
public class GuardrailService {

    private static final int  BOT_HORIZONTAL_CAP  = 100;
    private static final int  THREAD_DEPTH_CAP    = 20;
    private static final long COOLDOWN_MINUTES     = 10;

    private final StringRedisTemplate        redis;
    private final DefaultRedisScript<Long>   botCountScript;

    public GuardrailService(StringRedisTemplate redis,
                            DefaultRedisScript<Long> botCountScript) {
        this.redis          = redis;
        this.botCountScript = botCountScript;
    }

    // ── Horizontal Cap ────────────────────────────────────────────────────────

    /**
     * Atomically increments the bot-reply counter for a post.
     * @return true if the action is allowed (counter ≤ 100), false if cap exceeded.
     */
    public boolean checkAndIncrementBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";
        Long result = redis.execute(
                botCountScript,
                List.of(key),
                String.valueOf(BOT_HORIZONTAL_CAP)
        );
        // Lua returns -1 when cap is already at/above limit
        return result != null && result != -1L;
    }

    // ── Vertical Cap ─────────────────────────────────────────────────────────

    /**
     * @return true if the depth is within the allowed limit.
     */
    public boolean checkVerticalCap(int depthLevel) {
        return depthLevel <= THREAD_DEPTH_CAP;
    }

    // ── Cooldown Cap ─────────────────────────────────────────────────────────

    /**
     * Sets a 10-minute cooldown key for a bot-human pair (SET NX EX).
     * @return true if the key was newly set (interaction allowed),
     *         false if the key already existed (still in cooldown).
     */
    public boolean checkAndSetCooldown(Long botId, Long humanId) {
        String key = "cooldown:bot_" + botId + ":human_" + humanId;
        Boolean set = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(COOLDOWN_MINUTES));
        return Boolean.TRUE.equals(set);
    }
}
