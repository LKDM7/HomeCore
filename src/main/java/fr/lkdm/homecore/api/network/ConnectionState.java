package fr.lkdm.homecore.api.network;

/** Logical connection state; no distance or antenna simulation is implied. */
public enum ConnectionState {
    /** Device is reachable. */ CONNECTED,
    /** Device exists but cannot currently be reached. */ UNREACHABLE,
    /** Device is offline or unloaded. */ OFFLINE
}
