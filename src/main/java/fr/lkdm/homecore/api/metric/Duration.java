package fr.lkdm.homecore.api.metric;

/** Immutable duration measured in game ticks.
 * @param ticks nonnegative tick count
 */
public record Duration(long ticks) {
    /** Rejects negative durations. */
    public Duration { if (ticks < 0) throw new IllegalArgumentException("Negative duration"); }
}
