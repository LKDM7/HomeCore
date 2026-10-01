package fr.lkdm.homecore.api.production;

/** Receives finished-batch receipts on the publishing server thread. */
@FunctionalInterface
public interface ProductionListener {
    /**
     * Handles one finished batch. Implementations must be idempotent: the same
     * {@link ProductionReceipt#transactionId()} may be delivered more than once.
     *
     * @param receipt proof of a batch that really finished
     */
    void onProduced(ProductionReceipt receipt);
}
