package fr.lkdm.homecore.api.metric;

/** Immutable stored energy and capacity in FE.
 * @param stored nonnegative stored energy
 * @param capacity capacity, at least the stored energy
 */
public record Energy(long stored, long capacity) {
    /** Validates the storage invariant. */
    public Energy { if (stored < 0 || capacity < stored) throw new IllegalArgumentException("Invalid energy storage"); }
}
