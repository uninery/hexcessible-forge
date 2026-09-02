package dev.tizu.hexcessible.mixin;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.CastRef;
import dev.tizu.hexcessible.accessor.CastingInterfaceAccessor;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec2;

@Mixin(GuiSpellcasting.class)
public class DrawStateMixin implements DrawStateMixinAccessor {
    @Unique
    private CastRef castref;
    @Unique
    private DrawState state;
    @Unique
    private boolean noActing;

    @Shadow(remap = false)
    private InteractionHand handOpenedWith;
    @Shadow(remap = false)
    private List<ResolvedPattern> patterns;
    @Shadow(remap = false)
    private Set<HexCoord> usedSpots;

    @Inject(at = @At("HEAD"), method = "init")
    private void init(CallbackInfo info) {
        PatternEntries.INSTANCE.invalidateCaches();
        var castui = (GuiSpellcasting) (Object) this;
        var accessor = new CastingInterfaceAccessor(castui);
        castref = new CastRef(castui, accessor, handOpenedWith, patterns,
                usedSpots);
        state = DrawState.getNew(castref);
        noActing = !(Minecraft.getInstance().screen instanceof GuiSpellcasting);
    }

    @Inject(at = @At("HEAD"), method = "m_94757_", remap = false)
    private void mouseMoved(double mx, double my, CallbackInfo info) {
        state.onMouseMove(mx, my);
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked")
    private void mouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> info) {
        state.onMousePress(mx, my, button);
    }

    @Inject(at = @At("HEAD"), method = "mouseScrolled", cancellable = true)
    private void mouseScrolled(double mx, double my, double delta, CallbackInfoReturnable<Boolean> info) {
        if (state.onMouseScroll((int) delta))
            info.setReturnValue(true);
    }

    @Inject(at = @At("RETURN"), method = "render")
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta,
            CallbackInfo info) {
        if (!noActing && DrawState.shouldClose(state)) {
            ((GuiSpellcasting) (Object) this).onClose();
            return;
        }

        var nextState = DrawState.updateRequired((GuiSpellcasting) (Object) this, state);
        if (nextState != null)
            state = nextState;

        if (Hexcessible.cfg().debug) {
            renderDebug(ctx, state.getClass().getSimpleName(), 0);
            var debug = state.getDebugInfo();
            for (int i = 0; i < debug.size(); i++)
                renderDebug(ctx, debug.get(i), i + 1);
        }

        if (!noActing) {
            state.onRender(ctx, mouseX, mouseY);
            renderHints(ctx);
        }
    }

    @Unique
    private void renderDebug(GuiGraphics ctx, String text, int i) {
        ctx.drawString(Minecraft.getInstance().font,
                text, 5, 5 + (i * 10), 0xFFFFFF);
    }

    @Unique
    private void renderHints(GuiGraphics ctx) {
        if (!Hexcessible.cfg().shortcutHints)
            return;
        var hints = state.getHints();
        if (hints.isEmpty())
            return;

        var x = 6;
        var y = ctx.guiHeight() - 16;
        for (var hint : hints.entrySet()) {
            var text = Component.empty()
                    .append(Component.literal(hint.getKey() + " ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("hexcessible.hint." + hint.getValue())
                            .withStyle(ChatFormatting.DARK_GRAY));
            ctx.drawString(Minecraft.getInstance().font,
                    text, x, y, 0xFFFFFF);
            y -= 10;
        }
    }

    /**
     * Wraps GuiSpellcasting#drawStart: block the vanilla mouse-based draw start
     * while a drawstate (keyboard drawing, autocompleting, ...) is active.
     *
     * drawStart is a private Hex Casting method (unremapped), so we use
     * remap = false and the runtime name. (The Fabric original used MixinExtras'
     * @WrapMethod; Forge 47.1.x bundles MixinExtras 0.3.5 which predates it, so
     * this is a plain cancellable inject instead.)
     */
    @Inject(method = "drawStart", remap = false, at = @At("HEAD"), cancellable = true)
    private void drawStart(double mxOut, double myOut, CallbackInfoReturnable<Boolean> info) {
        if (!state.allowStartDrawing())
            info.setReturnValue(false);
    }

    @Override
    public DrawState state() {
        return state;
    }

    @Override
    public @Nullable HexPattern getPatternAt(int x, int y) {
        var coord = ((GuiSpellcasting) (Object) this).pxToCoord(new Vec2(x, y));
        return patterns.stream()
                .filter(p -> p.getOrigin().equals(coord)
                        || p.getPattern().positions().stream()
                                .map(pt -> pt.plus(p.getOrigin()))
                                .anyMatch(pt -> pt.equals(coord)))
                .findFirst()
                .map(ResolvedPattern::getPattern)
                .orElse(null);
    }

    @Override
    public void disallowTyping() {
        castref.disallowTyping();
    }
}
