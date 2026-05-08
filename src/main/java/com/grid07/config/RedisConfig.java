package com.grid07.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Lua script for atomic bot-count check-and-increment.
     * Returns the new count if allowed (<=cap), or -1 if the cap is exceeded.
     * This guarantees the horizontal cap holds exactly under concurrent load.
     */
    @Bean
    public DefaultRedisScript<Long> botCountScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local current = redis.call('GET', KEYS[1]) " +
            "if current == false then " +
            "  redis.call('SET', KEYS[1], 1) " +
            "  return 1 " +
            "elseif tonumber(current) < tonumber(ARGV[1]) then " +
            "  return redis.call('INCR', KEYS[1]) " +
            "else " +
            "  return -1 " +
            "end"
        );
        script.setResultType(Long.class);
        return script;
    }
}
