package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

/**
 * Routes typed characters to the active drawstate while the spellcasting
 * interface is open.
 *
 * The Fabric original injected into charTyped on ContainerEventHandler (yarn's
 * ParentElement), but the Mixin annotation processor rejects injectors in
 * interface mixins and Screen does not declare charTyped in Mojmap (it inherits
 * the interface default), so here we intercept the character input at the
 * source instead: KeyboardHandler#charTyped, which vanilla calls for every
 * typed character.
 */
@Mixin(KeyboardHandler.class)
public class DrawStateParentElemMixin {

    @Inject(method = "charTyped(JII)V", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof DrawStateMixinAccessor accessor) {
            accessor.state().onCharType((char) codePoint);
            ci.cancel();
        }
    }
}
