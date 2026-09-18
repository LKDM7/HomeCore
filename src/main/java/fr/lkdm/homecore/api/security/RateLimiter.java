package fr.lkdm.homecore.api.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Bounded per-player token buckets. Call before processing any control request,
 * including malformed or unauthorized requests. A bucket refills its complete
 * capacity over one refill period. Active buckets are never evicted to admit new
 * players, preventing UUID churn from resetting an active player's allowance.
 *
 * <p>Methods are synchronized. The injected clock must be monotonic like
 * {@link System#nanoTime()}; ordinary signed nanoTime wraparound is supported for
 * elapsed intervals shorter than 2^63 nanoseconds. No worker thread is created.</p>
 */
public final class RateLimiter {
    private final int capacity;
    private final long refillNanos;
    private final int maxKeys;
    private final LongSupplier nanoClock;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    /** Creates a limiter allowing bursts of 10 requests, refilling 10 per second, with 4096 players. */
    public RateLimiter() { this(10, Duration.ofSeconds(1), 4096, System::nanoTime); }

    /**
     * Creates a configurable limiter.
     *
     * @param capacity maximum burst allowance, strictly positive
     * @param refillPeriod time to refill an empty bucket, positive and representable in nanoseconds
     * @param maxKeys maximum simultaneously tracked players, strictly positive
     * @param nanoClock monotonic nanosecond clock
     */
    public RateLimiter(int capacity, Duration refillPeriod, int maxKeys, LongSupplier nanoClock) {
        if (capacity <= 0 || maxKeys <= 0) throw new IllegalArgumentException("Capacity and maximum keys must be positive");
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        long nanos;
        try { nanos = refillPeriod.toNanos(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Refill period exceeds nanosecond range", overflow); }
        if (nanos <= 0) throw new IllegalArgumentException("Refill period must be positive");
        this.capacity = capacity;
        this.refillNanos = nanos;
        this.maxKeys = maxKeys;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * Consumes one token if available. When storage is full, fully idle buckets
     * are removed before admitting a new player. New players are denied if all
     * tracked buckets are active. Rejected attempts extend the bucket's idle time.
     *
     * @param player authenticated player UUID
     * @return whether this request may proceed
     */
    public synchronized boolean tryAcquire(UUID player) {
        Objects.requireNonNull(player, "player");
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.get(player);
        if (bucket == null) {
            if (buckets.size() >= maxKeys) {
                buckets.values().removeIf(existing -> now - existing.lastAccess >= refillNanos);
            }
            if (buckets.size() >= maxKeys) return false;
            bucket = new Bucket(capacity, now);
            buckets.put(player, bucket);
        }
        long elapsed = now - bucket.lastRefill;
        if (elapsed > 0) {
            bucket.tokens = Math.min(capacity, bucket.tokens + (double) elapsed / refillNanos * capacity);
            bucket.lastRefill = now;
        }
        bucket.lastAccess = now;
        if (bucket.tokens < 1) return false;
        bucket.tokens -= 1;
        return true;
    }

    /**
     * Removes a player's bucket for trusted administrative cleanup. Avoid calling
     * on ordinary disconnects: reconnecting must not reset an exhausted allowance.
     *
     * @param player player UUID
     */
    public synchronized void remove(UUID player) { buckets.remove(Objects.requireNonNull(player, "player")); }

    /** Releases every bucket when the owning server stops. */
    public synchronized void clear() { buckets.clear(); }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;
        private long lastAccess;
        private Bucket(double tokens, long now) { this.tokens = tokens; lastRefill = now; lastAccess = now; }
    }
}
