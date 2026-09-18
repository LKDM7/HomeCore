package fr.lkdm.homecore.api.metric;

/** Immutable percentage on the inclusive scale 0 to 100.
 * @param value percentage, not a fraction
 */
public record Percentage(double value) {
    /** Rejects nonfinite values and values outside 0 to 100. */
    public Percentage {
        if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("Percentage must be between 0 and 100");
    }
}
