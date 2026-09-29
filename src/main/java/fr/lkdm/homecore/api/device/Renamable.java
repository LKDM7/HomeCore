package fr.lkdm.homecore.api.device;

import fr.lkdm.homecore.api.action.ActionResult;

/**
 * Optional contract for a {@link DashboardDevice} whose label a player can change remotely.
 * HomeCore then adds the standard {@link fr.lkdm.homecore.api.action.StandardActions#RENAME} text action to
 * the device schema; {@link DashboardDevice#displayName()} must reflect the new name. Call on the server thread.
 */
public interface Renamable {
    /** Longest accepted name, the anvil limit. */
    int MAX_LENGTH = 50;

    /** Applies a validated name from an authorized player.
     * @param name trimmed name without control characters; empty restores the default name
     * @return outcome; SUCCESS when the new name is in place
     */
    ActionResult rename(String name);
}
