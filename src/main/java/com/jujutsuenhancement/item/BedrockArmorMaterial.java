package com.jujutsuenhancement.item;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The numbers of one piece of equipment: the addon gives every piece its own {@code minecraft:wearable.protection},
 * {@code minecraft:durability.max_durability} and {@code minecraft:enchantable.value}.
 */
public record BedrockArmorMaterial(String name, ArmorItem.Type slotType, int protection, int durability, int enchantability)
        implements ArmorMaterial {
    @Override public int getDurabilityForType(ArmorItem.Type type) { return durability; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return type == slotType ? protection : 0; }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_GENERIC; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    @Override public String getName() { return JujutsuEnhancement.MODID + ":" + name; }
    @Override public float getToughness() { return 0.0F; }
    @Override public float getKnockbackResistance() { return 0.0F; }
}
