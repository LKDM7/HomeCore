package fr.lkdm.homecore.workbench.recipe;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Allocates overlapping ingredients without mutating the inventory. */
public final class BatchPlanner {
    private BatchPlanner() { }

    public static Optional<int[]> plan(List<CountedIngredient> materials, List<ItemStack> inventory, int assemblies) {
        if (assemblies < 1 || assemblies > 64 || materials.isEmpty() || materials.size() > 9 || inventory.size() > 9) {
            return Optional.empty();
        }
        int slots = inventory.size(), target = 1 + slots + materials.size(), nodes = target + 1;
        int[][] capacity = new int[nodes][nodes];
        int required = 0;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.get(slot);
            capacity[0][1 + slot] = stack.getCount();
            for (int material = 0; material < materials.size(); material++) {
                if (!stack.isEmpty() && materials.get(material).ingredient().test(stack)) {
                    capacity[1 + slot][1 + slots + material] = stack.getCount();
                }
            }
        }
        for (int material = 0; material < materials.size(); material++) {
            int count = materials.get(material).count() * assemblies;
            capacity[1 + slots + material][target] = count;
            required += count;
        }
        int flow = 0;
        while (flow < required) {
            int[] previous = new int[nodes];
            Arrays.fill(previous, -1);
            previous[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && previous[target] < 0) {
                int node = queue.remove();
                for (int next = 1; next < nodes; next++) {
                    if (previous[next] < 0 && capacity[node][next] > 0) {
                        previous[next] = node;
                        queue.add(next);
                    }
                }
            }
            if (previous[target] < 0) return Optional.empty();
            int amount = required - flow;
            for (int node = target; node != 0; node = previous[node]) amount = Math.min(amount, capacity[previous[node]][node]);
            for (int node = target; node != 0; node = previous[node]) {
                capacity[previous[node]][node] -= amount;
                capacity[node][previous[node]] += amount;
            }
            flow += amount;
        }
        int[] consumption = new int[slots];
        for (int slot = 0; slot < slots; slot++) consumption[slot] = capacity[slot + 1][0];
        return Optional.of(consumption);
    }

    public static int maxOutput(ElectronicsRecipe recipe, List<ItemStack> inventory, ItemStack outputStack) {
        ItemStack result = recipe.result();
        if (!outputStack.isEmpty() && !ItemStack.isSameItemSameComponents(result, outputStack)) return 0;
        int capacity = Math.min(64, result.getMaxStackSize()) - outputStack.getCount();
        int low = 0, high = Math.max(0, capacity / result.getCount());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (plan(recipe.materials(), inventory, middle).isPresent()) low = middle;
            else high = middle - 1;
        }
        return low * result.getCount();
    }
}
