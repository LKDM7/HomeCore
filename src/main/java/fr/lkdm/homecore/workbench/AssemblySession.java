package fr.lkdm.homecore.workbench;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server-owned reservation and prototype progress for one player and one block. */
public final class AssemblySession {
    final UUID id;
    final UUID player;
    final ResourceLocation recipe;
    final ItemStack result;
    final int steps;
    final int duration;
    /** Server game tick the batch was started at; a consumer uses it to reject retroactive credit. */
    long startedTick;
    fr.lkdm.homecore.api.production.ProductionStart start;
    int placedMask;
    int progress;
    int phase;

    AssemblySession(UUID id, UUID player, ResourceLocation recipe, ItemStack result, int steps, int duration, long startedTick) {
        this.id = id;
        this.player = player;
        this.recipe = recipe;
        this.result = result.copy();
        this.steps = steps;
        this.duration = duration;
        this.startedTick = startedTick;
        this.phase = 1;
    }

    boolean place(UUID playerId, UUID sessionId, int source, int target) {
        if (phase != 1 || !player.equals(playerId) || !id.equals(sessionId)
                || source < 0 || source >= steps || source != target || (placedMask & (1 << source)) != 0) return false;
        placedMask |= 1 << source;
        if (placedMask == (1 << steps) - 1) phase = 2;
        return true;
    }
}
