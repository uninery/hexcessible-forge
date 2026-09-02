package dev.tizu.hexcessible.smartsig;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexDir;
import dev.tizu.hexcessible.Utils;
import dev.tizu.hexcessible.entries.BookEntries;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public class HexThingsIntrojection implements SmartSig.Conditional {

    @Override
    public boolean enabled() {
        return ModList.get().isLoaded("hexthings");
    }

    public static final List<PatternEntries.Entry> ALL = List.of(
            make("unquote", HexDir.NORTH_EAST, "aqqq", "hexthings.book.patterns.spells.utils.unquote"));

    private static PatternEntries.Entry make(String id, HexDir dir, String sig,
            String desc) {
        var doc = new BookEntries.Entry("hexcessible:" + id, null,
                Component.translatable(desc).getString(), "", "", 0);
        var name = Component.translatable("hexcasting.action.hexthings:" + id)
                .getString();
        return new PatternEntries.Entry("hexcessible:" + id, name,
                () -> false, dir, List.of(Utils.angle(sig)), List.of(doc), 0);
    }

    @Override
    public @Nullable List<PatternEntries.Entry> get(String query) {
        return ALL;
    }

    @Override
    public @Nullable PatternEntries.Entry get(List<HexAngle> sig) {
        return ALL.stream()
                .filter(e -> e.sig().stream().anyMatch(s -> s.equals(sig)))
                .findFirst()
                .orElse(null);
    }
}
