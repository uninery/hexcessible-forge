package dev.tizu.hexcessible.compat;

import java.lang.reflect.Field;

import dev.tizu.hexcessible.Hexcessible;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Compatibility with ImmediatelyFast on Minecraft 1.20.1.
 *
 * <p>
 * ImmediatelyFast's 1.20.x builds (e.g. `ImmediatelyFast-Forge-1.5.5+1.20.4.jar`,
 * which declare support for [1.20,1.20.4]) break text rendering on 1.20.1 when
 * their `font_atlas_resizing` feature is active: the glyph atlas is enlarged
 * from 256x256 to 2048x2048, and when many glyphs are uploaded dynamically —
 * exactly what the hexcessible autocomplete panel does every frame — glyphs
 * come out as solid boxes (tofu), for ASCII and non-ASCII alike. Users can
 * work around it by setting `font_atlas_resizing` to false in
 * `config/immediatelyfast.json`, but this class does it automatically.
 *
 * <p>
 * IF reads the flag from its (public) runtime config each time a new glyph
 * atlas is created, and glyph atlases are only created once fonts load, which
 * happens after mod construction. Disabling the flag during FMLClientSetup
 * therefore makes every atlas 256x256 from the start — no user configuration
 * needed and no visual difference besides IF's minor texture-rebind saving.
 *
 * <p>
 * This is a mitigation for an IF/1.20.1 incompatibility, not a hexcessible
 * bug; hexcessible itself only uses standard {@code GuiGraphics}/{@code Font}
 * calls. If the IF internals ever change, the reflection below fails softly
 * and nothing else is affected.
 */
@Mod.EventBusSubscriber(modid = Hexcessible.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ImmediatelyFastCompat {

    private static final String IF_CLASS = "net.raphimc.immediatelyfast.ImmediatelyFast";
    private static final String IF_MODID = "immediatelyfast";

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded(IF_MODID))
            return;

        try {
            Class<?> ifClass = Class.forName(IF_CLASS);
            Field runtimeConfigField = ifClass.getField("runtimeConfig");
            Object runtimeConfig = runtimeConfigField.get(null);
            if (runtimeConfig == null) {
                Hexcessible.LOGGER.warn("ImmediatelyFast runtime config not initialized yet, skipping compat");
                return;
            }
            Field fontAtlasResizingField = runtimeConfig.getClass().getField("font_atlas_resizing");
            if (fontAtlasResizingField.getBoolean(runtimeConfig)) {
                fontAtlasResizingField.setBoolean(runtimeConfig, false);
                Hexcessible.LOGGER.warn(
                        "ImmediatelyFast detected: disabled its font_atlas_resizing feature, which renders "
                                + "text as boxes on Minecraft 1.20.1 (autocomplete panel of Hexcessible). "
                                + "Restart not required; no config changes were made.");
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Hexcessible.LOGGER.warn("Failed to disable ImmediatelyFast's font_atlas_resizing: {}", e.toString());
        }
    }

    private ImmediatelyFastCompat() {
    }
}
