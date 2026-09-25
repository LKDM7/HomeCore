package fr.lkdm.homecore.workbench.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One draggable material group and its target, in normalized board coordinates. */
public record AssemblyPart(String id, String label, int ingredientIndex, int x, int y) {
    public static final Codec<AssemblyPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.string(1, 64).fieldOf("id").forGetter(AssemblyPart::id),
            Codec.string(1, 128).fieldOf("label").forGetter(AssemblyPart::label),
            Codec.intRange(0, 8).fieldOf("ingredient_index").forGetter(AssemblyPart::ingredientIndex),
            Codec.intRange(0, 100).fieldOf("x").forGetter(AssemblyPart::x),
            Codec.intRange(0, 100).fieldOf("y").forGetter(AssemblyPart::y)
    ).apply(instance, AssemblyPart::new));

    public AssemblyPart {
        if (id == null || id.isBlank() || id.length() > 64 || label == null || label.isBlank()
                || label.length() > 128 || ingredientIndex < 0 || ingredientIndex > 8
                || x < 0 || x > 100 || y < 0 || y > 100) {
            throw new IllegalArgumentException("Invalid assembly part");
        }
    }
}
