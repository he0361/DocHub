package com.dochub.workbench.modelconfig.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis invalidation signal; the database remains the source of truth. */
@Component
public class ModelConfigVersionPublisher {
    public static final String CHANNEL = "dochub:model-config:version";

    private final StringRedisTemplate redisTemplate;

    public ModelConfigVersionPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(long version) {
        redisTemplate.convertAndSend(CHANNEL, Long.toString(version));
    }
}
