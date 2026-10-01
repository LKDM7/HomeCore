package fr.lkdm.homecore.api.stock;

import fr.lkdm.homecore.api.capability.DeviceCapability;
import net.minecraft.resources.ResourceLocation;

/**
 * Authorised, read-only view of the stock a device can account for.
 *
 * <p>This is the only contract a consumer needs: it never traverses a storage mod's
 * block entities, indices or inventories. Implementations run on the owning server
 * thread and must, for every call, re-check the requesting player's membership,
 * {@link fr.lkdm.homecore.api.security.Permission#VIEW} right on the requested
 * network, the device's own binding to that network, and any scope restriction of
 * their own. A request that fails any check returns
 * {@link StockSnapshot#unavailable}, never another network's data.</p>
 *
 * <p>Reading must have no side effect: no extraction, no movement, no reservation,
 * no forced chunk loading and no full rescan triggered merely because someone is
 * looking. A provider that cannot observe part of its scope reports
 * {@link StockAvailability#PARTIAL} rather than a smaller number presented as fact.</p>
 */
public interface StockProvider {
    /** Registered descriptor used to query this capability on a device. */
    DeviceCapability<StockProvider> CAPABILITY = new DeviceCapability<>(
            ResourceLocation.fromNamespaceAndPath("homecore", "stock_provider"), StockProvider.class);

    /**
     * Observes the requested variants for the requesting player.
     *
     * @param request bounded question scoped to one network and one authenticated player
     * @return observation limited to what this player is allowed to know
     */
    StockSnapshot observe(StockRequest request);
}
