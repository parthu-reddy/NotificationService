package com.fooddelivery.notification.service;

import com.fooddelivery.notification.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitingService {

    private final ProxyManager<byte[]> proxyManager;

    public RateLimitingService(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /**
     * Resolves the bucket for a specific target (e.g., Phone Number or User ID).
     */
    public BucketProxy resolveBucket(String targetIdentifier, String eventType) {
        String cacheKey = "rate_limit:" + eventType + ":" + targetIdentifier;
        
        BucketConfiguration configuration = BucketConfiguration.builder()
                // Greedy refill: tokens are added fractionally over time
                .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1))))
                .build();

        return proxyManager.builder().build(cacheKey.getBytes(), configuration);
    }

    /**
     * Executes the rate limit check.
     */
    public void enforceRateLimit(String targetIdentifier, String eventType) {
        BucketProxy bucket = resolveBucket(targetIdentifier, eventType);
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Rate limit exhausted for target: " + targetIdentifier);
        }
    }
}
