package fr.lkdm.homecore.api.event;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Lightweight synchronous event delivery with independently removable listeners.
 * Listener registration/removal is thread safe. Callbacks run on the publisher's
 * thread and should finish promptly. Publish Minecraft events on the server thread.
 * Closing a subscription prevents later callbacks; an already running callback
 * may finish. New listeners do not receive an event already being published.
 * Each failed subscription logs at most one warning until it is replaced.
 */
public final class DeviceEventBus implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CopyOnWriteArrayList<Subscription> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    /** Creates an empty event bus. */
    public DeviceEventBus() { }

    /** Registers one subscription; registering the same callback twice produces two deliveries.
     * @param listener callback owned until its subscription is closed
     * @return idempotently closeable subscription
     * @throws IllegalStateException if this bus is closed
     */
    public synchronized Subscription subscribe(DeviceEventListener listener) {
        requireOpen();
        Subscription subscription = new Subscription(Objects.requireNonNull(listener, "listener"));
        listeners.add(subscription);
        return subscription;
    }

    /** Registers a callback for one declared event type.
     * @param type event definition used as an identifier filter
     * @param listener callback
     * @return removable subscription
     */
    public Subscription subscribe(DeviceEventType type, DeviceEventListener listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        return subscribe(event -> {
            if (event.type().equals(type.id())) listener.onEvent(event);
        });
    }

    /** Delivers an event to listeners in registration order, isolating callback exceptions.
     * @param event immutable event
     * @throws IllegalStateException if this bus is closed
     */
    public void publish(DeviceEvent event) {
        Objects.requireNonNull(event, "event");
        requireOpen();
        for (Subscription subscription : listeners) subscription.deliver(event);
    }

    /** Returns the number of active subscriptions.
     * @return current count
     */
    public int listenerCount() { return listeners.size(); }

    /** Releases every subscription while keeping the bus available for reuse. */
    public synchronized void clear() {
        for (Subscription subscription : listeners) subscription.close();
    }

    /** Permanently closes the bus and releases all listener references; idempotent. */
    @Override
    public synchronized void close() {
        closed = true;
        clear();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Event bus is closed");
    }

    /** Handle which releases its callback when closed. */
    public final class Subscription implements AutoCloseable {
        private volatile DeviceEventListener listener;
        private final AtomicBoolean warned = new AtomicBoolean();

        private Subscription(DeviceEventListener listener) { this.listener = listener; }

        private void deliver(DeviceEvent event) {
            DeviceEventListener current = listener;
            if (current == null) return;
            try {
                current.onEvent(event);
            } catch (RuntimeException exception) {
                if (warned.compareAndSet(false, true)) LOGGER.warn("Device event listener failed for {}", event.type(), exception);
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
