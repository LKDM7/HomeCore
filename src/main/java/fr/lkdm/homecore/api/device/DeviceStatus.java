package fr.lkdm.homecore.api.device;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Immutable availability state and optional user-facing explanation.
 * @param state current availability
 * @param message optional explanation, copied defensively
 */
public record DeviceStatus(State state, Optional<Component> message) {
    /** Availability states understood by every consumer. */
    public enum State {
        /** Available for authorized actions. */ ONLINE,
        /** Unavailable or disconnected. */ OFFLINE,
        /** A warning condition; control still requires ONLINE. */ WARNING,
        /** Device reports a fault. */ ERROR,
        /** Intentionally disabled. */ DISABLED,
        /** Availability has not been determined. */ UNKNOWN
    }
    /** Message-free ONLINE status. */
    public static final DeviceStatus ONLINE = of(State.ONLINE);
    /** Message-free OFFLINE status. */
    public static final DeviceStatus OFFLINE = of(State.OFFLINE);
    /** Message-free WARNING status. */
    public static final DeviceStatus WARNING = of(State.WARNING);
    /** Message-free ERROR status. */
    public static final DeviceStatus ERROR = of(State.ERROR);
    /** Message-free DISABLED status. */
    public static final DeviceStatus DISABLED = of(State.DISABLED);
    /** Message-free UNKNOWN status. */
    public static final DeviceStatus UNKNOWN = of(State.UNKNOWN);

    /** Validates availability and copies the optional explanation. */
    public DeviceStatus {
        Objects.requireNonNull(state);
        message = Objects.requireNonNull(message).map(Component::copy);
    }

    /** Creates a status without an explanation.
     * @param state availability
     * @return message-free status
     */
    public static DeviceStatus of(State state) { return new DeviceStatus(state, Optional.empty()); }

    /** Returns this state with a user-facing explanation.
     * @param message explanation
     * @return new status with a defensive copy of the explanation
     */
    public DeviceStatus withMessage(Component message) {
        return new DeviceStatus(state, Optional.of(message));
    }

    /** Returns a defensive copy of the explanation.
     * @return optional explanation
     */
    @Override public Optional<Component> message() { return message.map(Component::copy); }
}
