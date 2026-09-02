package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import at.petrak.hexcasting.api.client.ClientRenderHelper;
import dev.tizu.hexcessible.Hexcessible;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;

@Mixin(ClientRenderHelper.class)
public class FloatiesMixin {
    private FloatiesMixin() {
    }

    /**
     * Hides the "floaties" (patterns floating around the player while casting)
     * when the config option is set. (The Fabric original used MixinExtras'
     * @WrapMethod; Forge 47.1.x bundles MixinExtras 0.3.5 which predates it, so
     * this is a plain cancellable inject instead.)
     */
    @Inject(method = "renderCastingStack", remap = false, at = @At("HEAD"), cancellable = true)
    private static void renderCastingStack(PoseStack ps, Player player, float pticks,
            CallbackInfo info) {
        if (Hexcessible.cfg().hideFloaties)
            info.cancel();
    }
}
