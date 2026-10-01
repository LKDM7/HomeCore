package fr.lkdm.homecore.api.stock;

/** What the requesting player may do with the stock the snapshot describes. */
public enum StockAccess {
    /**
     * The player may read the quantities and is also allowed to take the items out
     * through the provider's own interfaces. Nothing is reserved by reading.
     */
    READ_AND_WITHDRAW,
    /**
     * The player may read the quantities but may not withdraw. A consumer must say so
     * instead of promising the items are obtainable.
     */
    READ_ONLY
}
