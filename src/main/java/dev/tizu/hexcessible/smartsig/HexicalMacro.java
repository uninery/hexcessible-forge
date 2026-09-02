package dev.tizu.hexcessible.smartsig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexDir;
import dev.tizu.hexcessible.Utils;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class HexicalMacro implements SmartSig.Conditional {

    @Override
    public boolean enabled() {
        return ModList.get().isLoaded("hexical");
    }

    @Override
    public @Nullable List<PatternEntries.Entry> get(String query) {
        return getAllMacros().stream().map(m -> getFor(Utils.angle(m))).toList();
    }

    @Override
    public @Nullable PatternEntries.Entry get(List<HexAngle> sig) {
        var all = getAllMacros();
        for (var macro : all) {
            if (macro.equals(Utils.angle(sig)))
                return getFor(sig);
        }
        return null;
    }

    private List<String> getAllMacros() {
        var player = Minecraft.getInstance().player;
        var inventory = player.getInventory();

        var targetItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("hexical", "grimoire"));

        var ender = player.getEnderChestInventory();
        var enderStacks = new ArrayList<ItemStack>(ender.getContainerSize());
        for (var i = 0; i < ender.getContainerSize(); i++)
            enderStacks.add(ender.getItem(i));

        var stacks = Stream.of(inventory.items, inventory.offhand, inventory.armor,
                enderStacks)
                .flatMap(Collection::stream)
                .filter(stack -> stack.is(targetItem))
                .toList();
        return stacks.stream()
                .map(ItemStack::getTag)
                .filter(Objects::nonNull)
                .map(nbt -> nbt.getCompound("expansions"))
                .flatMap(nbt -> nbt.getAllKeys().stream())
                .toList();
    }

    private PatternEntries.Entry getFor(List<HexAngle> sig) {
        var i18nkey = Component.translatable("hexcessible.smartsig.grimoire").getString();
        return new PatternEntries.Entry("hexical:grimoire_macro/" + Utils.angle(sig),
                i18nkey, () -> false, HexDir.EAST, List.of(sig), List.of(), 0);
    }
}
