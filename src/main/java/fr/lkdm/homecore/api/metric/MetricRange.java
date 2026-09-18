package fr.lkdm.homecore.api.metric;

import java.math.BigDecimal;
import java.util.Objects;

/** Inclusive exact decimal range with a step anchored at its minimum.
 * @param min inclusive minimum
 * @param max inclusive maximum
 * @param step nonnegative increment; zero means continuous
 */
public record MetricRange(BigDecimal min, BigDecimal max, BigDecimal step) {
    /** Validates range bounds and step. */
    public MetricRange {
        Objects.requireNonNull(min, "min"); Objects.requireNonNull(max, "max"); Objects.requireNonNull(step, "step");
        if (min.compareTo(max) > 0 || step.signum() < 0) throw new IllegalArgumentException("Invalid range");
    }
    /** Constructs a finite double range.
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @param step increment, zero for continuous
     */
    public MetricRange(double min, double max, double step) { this(decimal(min), decimal(max), decimal(step)); }
    private static BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite range");
        return BigDecimal.valueOf(value);
    }
    /** Checks an exact numeric value.
     * @param value candidate value
     * @return whether the value satisfies bounds and step
     */
    public boolean accepts(BigDecimal value) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0
                && (step.signum() == 0 || value.subtract(min).remainder(step).signum() == 0);
    }
}
