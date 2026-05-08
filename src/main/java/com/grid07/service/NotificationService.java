package com.grid07.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based notification throttler.
 *
 * When a bot interacts with a user's post:
 *  - If the user has NOT received a notification in the last 15 min → send immediately
 *    and set a 15-min cooldown key.
 *  - If the user HAS a cooldown key → push the notification to a pending Redis list;
 *    the CRON sweeper will batch-summarise it later.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long NOTIF_COOLDOWN_MINUTES = 15;

    private final StringRedisTemplate redis;

    public NotificationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String cooldownKey(Long userId) {
        return "notif_cooldown:user:" + userId;
    }

    private String pendingKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    /**
     * Called whenever a bot interacts with a user's post.
     *
     * @param userId  the post owner (human user)
     * @param botName the bot's display name
     * @param message a human-readable interaction message
     */
    public void handleBotInteraction(Long userId, String botName, String message) {
        String cooldown = cooldownKey(userId);
        // SET NX with 15-minute TTL: succeeds only if key does NOT exist
        Boolean isFirstNotif = redis.opsForValue()
                .setIfAbsent(cooldown, "1", Duration.ofMinutes(NOTIF_COOLDOWN_MINUTES));

        if (Boolean.TRUE.equals(isFirstNotif)) {
            // No recent notification → send immediately
            log.info("Push Notification Sent to User {}: {}", userId, message);
        } else {
            // User already notified recently → buffer the message
            redis.opsForList().rightPush(pendingKey(userId), message);
            log.debug("Buffered notification for user {}: {}", userId, message);
        }
    }
}
