package fr.lkdm.homecore.api.production;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Server-scoped delivery of finished-batch receipts.
 *
 * <p>Producers publish on the server thread at the moment a result exists; consumers
 * subscribe and credit each {@link ProductionReceipt#transactionId()} once. The log
 * keeps no history: it is a notification channel, and durable bookkeeping belongs to
 * the consumer that needs it. A failing listener never prevents delivery to the
 * others and never fails the production itself.</p>
 */
public final class ProductionLog implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CopyOnWriteArrayList<Subscription> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final java.util.UUID epoch = java.util.UUID.randomUUID();
    /** Identifies this server execution independently of saved world ticks.
     * @return execution identity
     */
    public java.util.UUID epoch() { return epoch; }
    /** Allocates an order marker when work starts, before its eventual completion.
     * @return strictly ordered start marker scoped to this execution
     * @throws IllegalStateException if this log is closed
     */
    public ProductionStart startStamp() { requireOpen(); return new ProductionStart(epoch, nextSequence()); }
    private volatile boolean closed;

    /** Creates an empty log. */
    public ProductionLog() { }

    /** Returns the next intra-tick ordinal for a receipt issued in this server run.
     * @return strictly increasing ordinal
     */
    public long nextSequence() { return sequence.getAndIncrement(); }

    /** Registers a receipt listener.
     * @param listener callback owned until its subscription is closed
     * @return idempotently closeable subscription
     * @throws IllegalStateException if this log is closed
     */
    public synchronized Subscription subscribe(ProductionListener listener) {
        requireOpen();
        Subscription subscription = new Subscription(Objects.requireNonNull(listener, "listener"));
        listeners.add(subscription);
        return subscription;
    }

    /** Delivers a receipt to listeners in registration order, isolating callback failures.
     * @param receipt proof of a batch that really finished
     * @throws IllegalStateException if this log is closed
     */
    public void publish(ProductionReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        requireOpen();
        for (Subscription subscription : listeners) subscription.deliver(receipt);
    }

    /** Returns the number of active subscriptions.
     * @return current count
     */
    public int listenerCount() { return listeners.size(); }

    /** Releases every subscription while keeping the log usable. */
    public synchronized void clear() {
        for (Subscription subscription : listeners) subscription.close();
    }

    /** Permanently closes the log and releases all listener references; idempotent. */
    @Override
    public synchronized void close() {
        closed = true;
        clear();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Production log is closed");
    }

    /** Handle which releases its callback when closed. */
    public final class Subscription implements AutoCloseable {
        private volatile ProductionListener listener;
        private final AtomicBoolean warned = new AtomicBoolean();

        private Subscription(ProductionListener listener) { this.listener = listener; }

        private void deliver(ProductionReceipt receipt) {
            ProductionListener current = listener;
            if (current == null) return;
            try {
                current.onProduced(receipt);
            } catch (RuntimeException exception) {
                if (warned.compareAndSet(false, true)) {
                    LOGGER.warn("Production listener failed for {}", receipt.source(), exception);
                }
            }
        }

        /** Removes this listener and releases its reference; idempotent. */
        @Override
        public void close() {
            listener = null;
            listeners.remove(this);
        }
    }
}
