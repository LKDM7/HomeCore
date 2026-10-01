package fr.lkdm.homecore.api.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Process-wide registry of recipe description adapters.
 *
 * <p>The mod that owns a recipe type registers the adapter for it, so a consumer
 * never reimplements another mod's recipe semantics. A type with no registered
 * adapter is genuinely undescribable here, and a consumer must say so rather than
 * guess a result: dynamic recipes and unknown types have no safe description.</p>
 *
 * <p>Descriptions are derived from the recipes a server currently has loaded. After
 * a datapack reload a consumer must describe again instead of trusting a cache.</p>
 */
public final class RecipeDescriptors {
    private static final Map<RecipeType<?>, RecipeDescriptorProvider> PROVIDERS = new LinkedHashMap<>();

    private RecipeDescriptors() { }

    /**
     * Registers the adapter for a recipe type during mod setup.
     *
     * @param type recipe type owned by the calling mod
     * @param provider adapter producing neutral descriptions
     * @throws IllegalArgumentException if the type already has an adapter
     */
    public static synchronized void register(RecipeType<?> type, RecipeDescriptorProvider provider) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.putIfAbsent(type, provider) != null) {
            throw new IllegalArgumentException("Duplicate recipe descriptor provider for " + type);
        }
    }

    /**
     * Describes a recipe using the adapter registered for its type.
     *
     * @param holder recipe resolved from the current server recipe manager
     * @return description, or empty when the type is unsupported or cannot be described
     */
    public static Optional<RecipeDescriptor> describe(RecipeHolder<? extends Recipe<?>> holder) {
        Objects.requireNonNull(holder, "holder");
        RecipeDescriptorProvider provider;
        synchronized (RecipeDescriptors.class) {
            provider = PROVIDERS.get(holder.value().getType());
        }
        if (provider == null) return Optional.empty();
        try {
            return Objects.requireNonNull(provider.describe(holder), "description");
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Reports whether a recipe type has a registered adapter.
     * @param type recipe type
     * @return whether descriptions are available for it
     */
    public static synchronized boolean supports(RecipeType<?> type) {
        return PROVIDERS.containsKey(Objects.requireNonNull(type, "type"));
    }
}
