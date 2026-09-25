package fr.lkdm.homecore.workbench.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.crafting.Ingredient;

/** A material requirement for one assembly. */
public record CountedIngredient(Ingredient ingredient, int count) {
    public static final Codec<CountedIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
            Codec.intRange(1, 576).fieldOf("count").forGetter(CountedIngredient::count)
    ).apply(instance, CountedIngredient::new));

    public CountedIngredient {
        if (ingredient == null || ingredient.isEmpty() || count < 1 || count > 576) {
            throw new IllegalArgumentException("Invalid electronics material");
        }
    }
}
