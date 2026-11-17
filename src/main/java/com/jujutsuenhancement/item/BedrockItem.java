package com.jujutsuenhancement.item;

import java.util.List;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import com.jujutsuenhancement.text.BedrockText;
import org.jetbrains.annotations.Nullable;

/**
 * An item of the Bedrock addon. The addon puts the name and the description of an item in one {@code display_name}
 * string ("name\nline\nline..."); the first line is the item's name ({@code item.jujutsuenhancement.<id>}) and the rest
 * is kept in {@code item.jujutsuenhancement.<id>.lore} with its {@code §} formatting, shown as the tooltip.
 */
public class BedrockItem extends Item {
    private final boolean foil;

    public BedrockItem(Properties properties) {
        this(properties, false);
    }

    public BedrockItem(Properties properties, boolean foil) {
        super(properties);
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return name(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        addLore(this, tooltip);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** Adds the description lines of {@code item} (its ".lore" translation, one tooltip line per "\n"). */
    public static void addLore(Item item, List<Component> tooltip) {
        String key = item.getDescriptionId() + ".lore";
        Language language = Language.getInstance();
        if (!language.has(key)) return;
        tooltip.addAll(BedrockText.lines(language.getOrDefault(key)));
    }

    /** The name with Bedrock's formatting rules (a colour code keeps the bold / italic before it). */
    public static Component name(Item item) {
        Language language = Language.getInstance();
        String key = item.getDescriptionId();
        return language.has(key) ? BedrockText.parse(language.getOrDefault(key)) : Component.translatable(key);
    }
}
