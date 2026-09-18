package fr.lkdm.homecore.api.security;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    @Test void burstAndPartialRefillArePerPlayer() {
        var clock = new AtomicLong();
        var limiter = new RateLimiter(2, Duration.ofSeconds(1), 10, clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player));
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
        clock.set(499_999_999L);
        assertFalse(limiter.tryAcquire(player));
        clock.set(500_000_000L);
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
        clock.set(100_000_000_000L);
        assertTrue(limiter.tryAcquire(player));
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
    }

    @Test void fullStorageNeverEvictsAnActiveExhaustedBucket() {
        var clock = new AtomicLong();
        var limiter = new RateLimiter(1, Duration.ofSeconds(1), 1, clock::get);
        UUID active = UUID.randomUUID(), newcomer = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(active));
        assertFalse(limiter.tryAcquire(newcomer));
        assertFalse(limiter.tryAcquire(active));
        clock.set(900_000_000L);
        assertFalse(limiter.tryAcquire(active));
        clock.set(1_000_000_000L);
        assertFalse(limiter.tryAcquire(newcomer));
        clock.set(1_900_000_000L);
        assertTrue(limiter.tryAcquire(newcomer));
        assertFalse(limiter.tryAcquire(active));
    }

    @Test void supportsSignedNanotimeWrapAndCleanup() {
        var clock = new AtomicLong(Long.MAX_VALUE - 50);
        var limiter = new RateLimiter(1, Duration.ofNanos(100), 1, clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player));
        clock.set(Long.MIN_VALUE + 49);
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
        limiter.remove(player);
        assertTrue(limiter.tryAcquire(player));
        limiter.clear();
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
    }

    @Test void validatesConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, Duration.ofSeconds(1), 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ZERO, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ofSeconds(1), 0, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ofSeconds(Long.MAX_VALUE), 1, () -> 0));
    }
}
