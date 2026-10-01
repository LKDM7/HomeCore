package fr.lkdm.homecore.api.production;

import java.util.Objects;
import java.util.UUID;

/** Order within a server run. Persist the epoch together with the ordinal across reloads.
 * @param epoch identity of the server execution
 * @param ordinal order allocated by its production log
 */
public record ProductionStart(UUID epoch, long ordinal) {
    /** Creates a validated start marker.
     * @param epoch identity of the server execution
     * @param ordinal nonnegative order within that execution
     */
    public ProductionStart {
        Objects.requireNonNull(epoch, "epoch");
        if (ordinal < 0) throw new IllegalArgumentException("Negative production order");
    }
}
