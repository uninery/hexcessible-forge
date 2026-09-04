package dev.tizu.hexcessible.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.drawstate.AutoCompleting;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.drawstate.Idling;
import dev.tizu.hexcessible.drawstate.KeyboardDrawing;
import dev.tizu.hexcessible.drawstate.MouseDrawing;
import dev.tizu.hexcessible.gui.BookOverlay;
import dev.tizu.hexcessible.keybinds.KeyBinds;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public class DrawStateScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DrawStateMixinAccessor accessor))
            return;
        var state = accessor.state();
        var overlayFocused = BookOverlay.isOverlayFocused();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Esc only exits the casting interface. It never closes the
            // floating Hex Notebook - close that with its × button or the
            // docs key instead.
            state.requestExit();
            cir.setReturnValue(true);
            return;
        }

        if (!overlayFocused
                && KeyBinds.keyMatches(KeyBinds.Action.MODE_TOGGLE, keyCode, modifiers)) {
            if (modeToggleAllowed(state)) {
                var cfg = Hexcessible.cfg().keyboardDraw;
                cfg.absoluteMode = !cfg.absoluteMode;
                Hexcessible.cfg().markDirty();
            }
            cir.setReturnValue(true);
            return;
        }

        if (!overlayFocused
                && KeyBinds.keyMatches(KeyBinds.Action.DOCS, keyCode, modifiers)
                && BookOverlay.docsActionAllowed(state)) {
            BookOverlay.toggleDocs();
            cir.setReturnValue(true);
            return;
        }

        if (BookOverlay.isVisible()
                && BookOverlay.onKeyPressed(keyCode, scanCode, modifiers, state)) {
            cir.setReturnValue(true);
            return;
        }

        state.onKeyPress(keyCode, modifiers);
        cir.setReturnValue(true);
    }

    /** Whether the mode toggle may fire right now. */
    private static boolean modeToggleAllowed(DrawState state) {
        if (state instanceof Idling || state instanceof MouseDrawing
                || state instanceof AutoCompleting)
            return true;
        if (state instanceof KeyboardDrawing kbd)
            return kbd.isBlankTyping();
        return false;
    }

    /** The casting interface went away (switched screen or closed). */
    @Inject(method = "removed", at = @At("HEAD"))
    private void onScreenRemoved(CallbackInfo ci) {
        if ((Object) this instanceof DrawStateMixinAccessor)
            BookOverlay.onCastUiClosed();
    }
}
