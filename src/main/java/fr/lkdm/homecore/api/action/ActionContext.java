package fr.lkdm.homecore.api.action;

import java.util.Objects;
import java.util.UUID;

/** Invocation identity, populated by trusted server code, never trusted from a client.
 *
     * @param playerId requesting player
 *
     * @param networkId containing network
 *
     * @param deviceId target device
 */
public record ActionContext(UUID playerId, UUID networkId, UUID deviceId) {
    /** Creates a context with non-null identities. */
    public ActionContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(deviceId, "deviceId");
    }
}
