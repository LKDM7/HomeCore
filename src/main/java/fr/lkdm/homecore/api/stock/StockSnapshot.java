package fr.lkdm.homecore.api.stock;

import java.util.List;
import java.util.Objects;

/**
 * A server-side observation of authorised stock at one moment.
 *
 * <p>A snapshot is an observation, never a reservation: another player may consume
 * the same items a tick later. It is also never persisted as an inventory. A
 * consumer must distinguish {@link StockAvailability#UNAVAILABLE} from an empty
 * {@link StockAvailability#COMPLETE} result — unknown is not zero.</p>
 *
 * @param availability how much of the requested scope was actually observed
 * @param access what the requesting player may do with the items
 * @param entries observed variants; a requested variant absent from a COMPLETE snapshot is absent from the scope
 * @param revision provider revision the observation was taken at, for cache invalidation
 * @param observedTick server game tick of the observation; monotonic and unaffected by {@code /time set}
 */
public record StockSnapshot(StockAvailability availability, StockAccess access,
                            List<StockEntry> entries, long revision, long observedTick) {
    /** Validates the observation and copies its entries. */
    public StockSnapshot {
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(access, "access");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (availability == StockAvailability.UNAVAILABLE && !entries.isEmpty()) {
            throw new IllegalArgumentException("An unavailable snapshot cannot report quantities");
        }
        if (entries.size() > StockRequest.MAX_VARIANTS) throw new IllegalArgumentException("Snapshot exceeds the requested variant bound");
    }

    /** Returns an observation that proves nothing about quantities.
     * @param observedTick server game tick of the failed observation
     * @return unavailable snapshot
     */
    public static StockSnapshot unavailable(long observedTick) {
        return new StockSnapshot(StockAvailability.UNAVAILABLE, StockAccess.READ_ONLY, List.of(), 0L, observedTick);
    }
}
