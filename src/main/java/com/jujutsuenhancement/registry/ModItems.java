package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.item.BedrockArmorItem;
import com.jujutsuenhancement.item.BedrockItem;
import com.jujutsuenhancement.item.BedrockSpawnEggItem;
import com.jujutsuenhancement.item.KatanaItem;
import com.jujutsuenhancement.item.RoughCrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every item of the Bedrock addon that has been ported. Items registered through {@link #reg} are listed, in this order,
 * in the "Jujutsu Awakening" creative tab (see {@link ModCreativeTabs}); the addon's own creative menu puts them in
 * the equipment / items categories.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JujutsuEnhancement.MODID);

    /** Items shown in the mod's creative tab, in registration order. */
    public static final List<RegistryObject<? extends Item>> TAB = new ArrayList<>();

    private static <T extends Item> RegistryObject<T> reg(String name, Supplier<T> item) {
        RegistryObject<T> object = ITEMS.register(name, item);
        TAB.add(object);
        return object;
    }

    // ---- spawn eggs (also in the vanilla spawn egg tab, see ModEvents)

    public static final RegistryObject<Item> GOJO_AWAKENED_SPAWN_EGG =
            reg("gojo_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.GOJO_AWAKENED, 0xEFEFEF, 0x2A5BD7, new Item.Properties()));

    public static final RegistryObject<Item> MAHITO_AWAKENED_SPAWN_EGG =
            reg("mahito_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.MAHITO_AWAKENED, 0x7C8FB5, 0x3B2A52, new Item.Properties()));

    public static final RegistryObject<Item> SUKUNA_AWAKENED_SPAWN_EGG =
            reg("sukuna_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.SUKUNA_AWAKENED, 0x8B1A1A, 0x111111, new Item.Properties()));

    public static final RegistryObject<Item> YUJI_AWAKENED_SPAWN_EGG =
            reg("yuji_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.YUJI_AWAKENED, 0xE8A0B0, 0x222222, new Item.Properties()));

    public static final RegistryObject<Item> HIGURUMA_AWAKENED_SPAWN_EGG =
            reg("higuruma_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.HIGURUMA_AWAKENED, 0x3A3A44, 0xC9A227, new Item.Properties()));

    // ---- items of the addon (items/*.json of the behavior pack)

    /** ds:unobtanium_ingot, "超导矿石块": a plain item (menu category "items"). */
    public static final RegistryObject<Item> UNOBTANIUM_INGOT =
            reg("unobtanium_ingot", () -> new BedrockItem(new Item.Properties().stacksTo(64)));

    /** ds:rough_crystal, "粗糙咒晶": a cursed object (咒物), foil, stack 64; relics/rough_crystal.mcfunction. */
    public static final RegistryObject<Item> ROUGH_CRYSTAL =
            reg("rough_crystal", () -> new RoughCrystalItem(new Item.Properties().stacksTo(64)));

    // ---- equipment (items/armour/*.json + attachables/fits/*.json); numbers are protection / durability / enchantability / damage chance

    /** ds:pigs_crown, "猪之冠", helmet, protection 6. The attachable has no animation. */
    public static final RegistryObject<Item> PIGS_CROWN =
            reg("pigs_crown", () -> new BedrockArmorItem("pigs_crown", ArmorItem.Type.HELMET, 6, 9999, 14, 60, 100, null));

    /** ds:gojo_cloak, "决战之袍", chestplate, protection 1, idle animation animation.gojo_cloak.idle. */
    public static final RegistryObject<Item> GOJO_CLOAK =
            reg("gojo_cloak", () -> new BedrockArmorItem("gojo_cloak", ArmorItem.Type.CHESTPLATE, 1, 9999, 14, 60, 100,
                    "animation.gojo_cloak.idle"));

    /** ds:kusakabe_fit, "最强の穿搭", leggings (its model is a whole outfit), protection 1. */
    public static final RegistryObject<Item> KUSAKABE_FIT =
            reg("kusakabe_fit", () -> new BedrockArmorItem("kusakabe_fit", ArmorItem.Type.LEGGINGS, 1, 9999, 14, 60, 100, null));

    /** ds:flyhead spawn egg; the addon's colours "#1144262" / "#000000" (the first has 7 digits, Bedrock keeps the low 24 bits). */
    public static final RegistryObject<Item> FLYHEAD_SPAWN_EGG =
            reg("flyhead_spawn_egg", () -> new BedrockSpawnEggItem(ModEntities.FLYHEAD, 0x144262, 0x000000, new Item.Properties()));

    private ModItems() {}

    // ---- weapons (咒具)

    /** ds:katana, "武士刀": the four-hit combo and three skills, see {@link com.jujutsuenhancement.combat.KatanaCombat}. */
    public static final RegistryObject<Item> KATANA =
            reg("katana", () -> new KatanaItem(new Item.Properties().stacksTo(1)));
}
