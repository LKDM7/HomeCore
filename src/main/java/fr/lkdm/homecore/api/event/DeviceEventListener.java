package fr.lkdm.homecore.api.event;

/** Callback invoked synchronously on the publishing thread. */
@FunctionalInterface
public interface DeviceEventListener {
    /** Handles an event; Minecraft state must only be accessed on its owning thread.
     * @param event immutable event
     */
    void onEvent(DeviceEvent event);
}
