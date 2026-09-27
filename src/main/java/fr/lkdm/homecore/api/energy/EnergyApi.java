package fr.lkdm.homecore.api.energy;

import fr.lkdm.homecore.api.metric.Unit;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Shared HomeLink energy contract: the HE unit and the block capability through which
 * machines expose their energy ports. HE has no conversion to any other energy unit.
 */
public final class EnergyApi {
    /** HomeLink Energy, an amount. */
    public static final Unit HE = new Unit(ResourceLocation.fromNamespaceAndPath("homecore", "he"), "HE");
    /** HomeLink Energy flow per game tick. */
    public static final Unit HE_PER_TICK = new Unit(ResourceLocation.fromNamespaceAndPath("homecore", "he_per_tick"), "HE/t");

    /**
     * Energy port of a block on the queried face. A {@code null} face means the block itself,
     * regardless of side. Providers register it in {@code RegisterCapabilitiesEvent}.
     */
    public static final BlockCapability<EnergyPort, Direction> BLOCK =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("homecore", "energy_port"), EnergyPort.class);

    private EnergyApi() { }

    /**
     * Adds two nonnegative energy amounts without overflowing.
     *
     * @param a first amount
     * @param b second amount
     * @return the sum, or {@link Long#MAX_VALUE} on overflow
     */
    public static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? (a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE) : sum;
    }
}
