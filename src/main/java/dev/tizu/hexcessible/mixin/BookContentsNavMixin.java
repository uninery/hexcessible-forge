package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tizu.hexcessible.gui.BookOverlay;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.client.book.gui.GuiBook;

/**
 * While the floating book overlay is shown over the casting interface,
 * Patchouli's book navigation ({@link BookContents#openLexiconGui}) must not
 * switch Minecraft to a fullscreen book screen (that would yank the player
 * out of the casting interface). Instead the target GUI is installed into the
 * overlay. Once the casting UI is gone the overlay is inactive and navigation
 * (e.g. opening the notebook item from the inventory) works normally again.
 */
@Mixin(BookContents.class)
public class BookContentsNavMixin {

    @Inject(method = "openLexiconGui", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexcessible$routeIntoOverlay(GuiBook gui, boolean push,
            CallbackInfo ci) {
        if (!BookOverlay.isActive())
            return;
        if (!gui.canBeOpened()) {
            // vanilla silently ignores locked/unopenable books too
            ci.cancel();
            return;
        }
        BookOverlay.navigateTo(gui, push);
        ci.cancel();
    }
}
