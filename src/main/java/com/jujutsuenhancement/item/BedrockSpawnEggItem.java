package com.jujutsuenhancement.item;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;

/** A spawn egg named like the addon's "item.spawn_egg.entity.ds:<id>.name" (with Bedrock's colour rules). */
public class BedrockSpawnEggItem extends ForgeSpawnEggItem {
    public BedrockSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Properties properties) {
        super(type, background, highlight, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return BedrockItem.name(this);
    }
}
