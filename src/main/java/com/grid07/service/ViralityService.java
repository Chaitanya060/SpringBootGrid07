package com.grid07.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViralityService {

    private final StringRedisTemplate redis;

    private static final int BOT_REPLY_POINTS    = 1;
    private static final int HUMAN_LIKE_POINTS   = 20;
    private static final int HUMAN_COMMENT_POINTS = 50;

    public ViralityService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String viralityKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }

    public void incrementBotReply(Long postId) {
        redis.opsForValue().increment(viralityKey(postId), BOT_REPLY_POINTS);
    }

    public void incrementHumanLike(Long postId) {
        redis.opsForValue().increment(viralityKey(postId), HUMAN_LIKE_POINTS);
    }

    public void incrementHumanComment(Long postId) {
        redis.opsForValue().increment(viralityKey(postId), HUMAN_COMMENT_POINTS);
    }

    public Long getViralityScore(Long postId) {
        String val = redis.opsForValue().get(viralityKey(postId));
        return val == null ? 0L : Long.parseLong(val);
    }
}
