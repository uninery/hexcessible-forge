package dev.tizu.hexcessible;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entrypoint for Hexcessible.
 *
 * <p>
 * The Fabric original registered its config screen through ModMenu; on Forge we
 * register it as a config screen extension point instead, which adds a
 * "Config" button in the mod list.
 */
@Mod(Hexcessible.MOD_ID)
public class HexcessibleForge {
    public HexcessibleForge() {
        // "unsafe" only means DistExecutor skips its referent-safety scan here;
        // the referenced method touches client-only classes (ConfigScreenHandler)
        // and is only ever invoked on the client.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> HexcessibleForge::registerConfigScreen);
    }

    private static void registerConfigScreen() {
        Hexcessible.cfg();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                        AutoConfig.getConfigScreen(HexcessibleConfig.class, parent).get()));
    }
}
