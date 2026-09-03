package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import vazkii.patchouli.client.book.gui.GuiBook;

/**
 * Patchouli book zoom used by the floating-book overlay when mapping window
 * coordinates onto the book GUI.
 */
@Mixin(value = GuiBook.class, remap = false)
public interface HexcessibleGuiBookAccessor {
    @Accessor("scaleFactor")
    float hexcessible$scaleFactor();
}
