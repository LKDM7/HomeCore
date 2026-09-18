package fr.lkdm.homecore.api.action;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Outcome of validation or execution.
 *
     * @param code machine-readable outcome
 *
     * @param message optional human-readable explanation
 */
public record ActionResult(Code code, Optional<Component> message) {
    /** Stable result codes for consumers. */
    public enum Code {
        /** Successful validation or execution. */
        SUCCESS,
        /** Caller lacks permission. */
        DENIED,
        /** Parameter failed validation. */
        INVALID_PARAMETER,
        /** Target is unavailable. */
        DEVICE_OFFLINE,
        /** Execution failed. */
        FAILED,
        /** Request frequency exceeded the limit. */
        RATE_LIMITED
    }

    /** Creates a result and defensively copies the optional message. */
    public ActionResult {
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").map(Component::copy);
    }

    /** Returns a defensive copy of the explanation.
     * @return optional explanation
     */
    @Override
    public Optional<Component> message() { return message.map(Component::copy); }

    /** Creates a successful outcome.
     * @return successful result */
    public static ActionResult success() { return of(Code.SUCCESS); }

    /** Creates an outcome with the specified code.
     * @param code outcome
     * @return result without a message */
    public static ActionResult of(Code code) { return new ActionResult(code, Optional.empty()); }

    /** Creates an outcome with the specified code.
     * @param code outcome
     * @param message explanation
     * @return result */
    public static ActionResult of(Code code, Component message) {
        return new ActionResult(code, Optional.of(message));
    }

    /** Tests whether the outcome is successful.
     * @return whether the operation succeeded */
    public boolean isSuccess() { return code == Code.SUCCESS; }
}
