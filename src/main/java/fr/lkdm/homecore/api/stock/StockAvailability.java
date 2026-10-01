package fr.lkdm.homecore.api.stock;

/** How much of the requested scope a snapshot actually observed. */
public enum StockAvailability {
    /**
     * Every source in the requested scope was observed. Absence from the snapshot
     * means the variant is genuinely absent, not merely unknown.
     */
    COMPLETE,
    /**
     * Part of the scope could not be observed, for example an unloaded inventory or an
     * index still being rebuilt. Reported quantities are real, but a missing variant
     * proves nothing.
     */
    PARTIAL,
    /**
     * Nothing could be observed: the provider is offline, unpowered, out of range or
     * the reader lost access. Quantities are unknown, which is never the same as zero.
     */
    UNAVAILABLE
}
