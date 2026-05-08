package com.grid07.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * CRON Sweeper — runs every 5 minutes.
 *
 * Scans Redis for all users with pending (buffered) notifications,
 * pops every message, logs a summarised push, then clears the list.
 *
 * Log format:
 *   "Summarized Push Notification: Bot X and [N] others interacted with your posts."
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final StringRedisTemplate redis;

    public NotificationScheduler(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Runs every 5 minutes (300_000 ms). In production this would be 15 minutes.
    @Scheduled(fixedRate = 300_000)
    public void sweepPendingNotifications() {
        log.info("[CRON] Sweeping pending notifications...");

        Set<String> pendingKeys = redis.keys("user:*:pending_notifs");
        if (pendingKeys == null || pendingKeys.isEmpty()) {
            log.info("[CRON] No pending notifications found.");
            return;
        }

        for (String key : pendingKeys) {
            List<String> messages = redis.opsForList().range(key, 0, -1);
            if (messages == null || messages.isEmpty()) continue;

            // Pop everything atomically (delete list after reading)
            redis.delete(key);

            // Build summary: first bot name + count of remaining interactions
            String firstMessage = messages.get(0);
            String firstBotName = extractBotName(firstMessage);
            int othersCount = messages.size() - 1;

            String userId = extractUserId(key);
            if (othersCount == 0) {
                log.info("Summarized Push Notification to User {}: {} interacted with your posts.",
                        userId, firstBotName);
            } else {
                log.info("Summarized Push Notification to User {}: {} and [{}] others interacted with your posts.",
                        userId, firstBotName, othersCount);
            }
        }
    }

    /** Extracts the bot name from a message like "BotAlpha replied to your post". */
    private String extractBotName(String message) {
        if (message == null || message.isBlank()) return "Unknown Bot";
        int spaceIdx = message.indexOf(' ');
        return spaceIdx > 0 ? message.substring(0, spaceIdx) : message;
    }

    /** Extracts the user ID from a Redis key like "user:42:pending_notifs". */
    private String extractUserId(String key) {
        String[] parts = key.split(":");
        return parts.length >= 2 ? parts[1] : "unknown";
    }
}
