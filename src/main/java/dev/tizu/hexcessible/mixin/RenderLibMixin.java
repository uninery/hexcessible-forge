package dev.tizu.hexcessible.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import at.petrak.hexcasting.client.render.RenderLib;
import dev.tizu.hexcessible.Hexcessible;
import net.minecraft.world.phys.Vec2;

@Mixin(RenderLib.class)
public class RenderLibMixin {

    /**
     * Skips the "zappy" line animation when the config option is set.
     * (The Fabric original used MixinExtras' @WrapMethod; Forge 47.1.x bundles
     * MixinExtras 0.3.5 which predates it, so this is a plain cancellable
     * inject instead.)
     */
    @Inject(method = "makeZappy", remap = false, at = @At("HEAD"), cancellable = true)
    private static void prefersReducedZappiness(List<Vec2> barePoints,
            Set<Integer> dupIndices, int hops, float variance, float speed,
            float flowIrregular, float readabilityOffset, float lastSegLenProp,
            double seed, CallbackInfoReturnable<List<Vec2>> info) {
        if (Hexcessible.cfg().prefersReducedMotion)
            info.setReturnValue(barePoints);
    }

}
