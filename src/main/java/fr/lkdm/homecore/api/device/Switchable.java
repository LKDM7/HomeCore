package fr.lkdm.homecore.api.device;

import fr.lkdm.homecore.api.action.ActionResult;

/**
 * Optional contract for a {@link DashboardDevice} that a player can switch on and off remotely.
 * HomeCore then adds the standard {@link fr.lkdm.homecore.api.action.StandardActions#POWER} toggle to the
 * device schema and transports the current state in device snapshots. Call every method on the server thread.
 *
 * <p>A switched-off device should keep reporting a status other than {@code OFFLINE} while it is loaded,
 * so that it can be switched on again.</p>
 */
public interface Switchable {
    /** Current state chosen by the player, independent of whether the machine is actually working.
     * @return true when the device is switched on
     */
    boolean powered();

    /** Applies a validated request from an authorized player.
     * @param powered requested state
     * @return outcome; SUCCESS when the device now has the requested state
     */
    ActionResult setPowered(boolean powered);
}
