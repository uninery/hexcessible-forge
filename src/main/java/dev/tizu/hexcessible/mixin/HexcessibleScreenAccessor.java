package dev.tizu.hexcessible.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * Lets the floating-book overlay reset an existing (reused) Screen instance
 * as if it were freshly shown. Accessor mixins go through the refmap, so the
 * field lookups work in the SRG-obfuscated production environment where
 * literal reflection on "children"/"narratables"/"initialized" fails.
 */
@Mixin(Screen.class)
public interface HexcessibleScreenAccessor {
    @Accessor("children")
    List<GuiEventListener> hexcessible$children();

    @Accessor("narratables")
    List<NarratableEntry> hexcessible$narratables();

    @Accessor("initialized")
    boolean hexcessible$initialized();

    @Accessor("initialized")
    void hexcessible$setInitialized(boolean initialized);
}
