package fr.lkdm.homecore.compat.rei;

import static fr.lkdm.homecore.compat.ElectronicsRecipeViews.*;

import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;

final class ElectronicsReiCategory implements DisplayCategory<ElectronicsReiDisplay> {
    private static final int PADDING = 5;

    @Override public CategoryIdentifier<? extends ElectronicsReiDisplay> getCategoryIdentifier() { return ElectronicsReiDisplay.CATEGORY; }

    @Override public Component getTitle() { return Component.translatable(HomeCoreWorkbench.BLOCK.get().getDescriptionId()); }

    @Override public Renderer getIcon() { return EntryStacks.of(HomeCoreWorkbench.BLOCK.get()); }

    @Override public int getDisplayWidth(ElectronicsReiDisplay display) { return WIDTH + PADDING * 2; }

    @Override public int getDisplayHeight() { return HEIGHT + PADDING * 2; }

    @Override public List<Widget> setupDisplay(ElectronicsReiDisplay display, Rectangle bounds) {
        int left = bounds.x + PADDING, top = bounds.y + PADDING, middle = top + (HEIGHT - SLOT) / 2;
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        for (int i = 0; i < display.getInputEntries().size(); i++) {
            widgets.add(Widgets.createSlot(new Point(left + slotX(i) + 1, top + slotY(i) + 1))
                    .entries(display.getInputEntries().get(i)).markInput());
        }
        widgets.add(Widgets.createArrow(new Point(left + ARROW_X, middle)));
        widgets.add(Widgets.createResultSlotBackground(new Point(left + OUTPUT_X + 1, middle + 1)));
        widgets.add(Widgets.createSlot(new Point(left + OUTPUT_X + 1, middle + 1))
                .entries(display.getOutputEntries().getFirst()).disableBackground().markOutput());
        return widgets;
    }
}
