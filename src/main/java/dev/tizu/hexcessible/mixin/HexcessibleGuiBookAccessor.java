package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.GuiGraphics;
import vazkii.patchouli.client.book.gui.GuiBook;

/**
 * Gives the floating-book overlay access to Patchouli's page-drawing
 * internals so it can render the book windowed (translated to the floating
 * window position, without the fullscreen background) straight onto the
 * casting screen. Patchouli classes are never SRG-remapped, so the names are
 * literal.
 */
@Mixin(value = GuiBook.class, remap = false)
public interface HexcessibleGuiBookAccessor {
    @Accessor("scaleFactor")
    float hexcessible$scaleFactor();

    @Invoker("resetTooltip")
    void hexcessible$resetTooltip();

    @Invoker("drawBackgroundElements")
    void hexcessible$drawBackgroundElements(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    @Invoker("drawForegroundElements")
    void hexcessible$drawForegroundElements(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    @Invoker("drawTooltip")
    void hexcessible$drawTooltip(GuiGraphics graphics, int mouseX, int mouseY);
}
