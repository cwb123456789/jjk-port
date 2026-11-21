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

    // ---- spawn eggs (also in the vanilla spawn egg tab, see ModEvents). The Java mod's characters (Gojo, Mahito,
    // Sukuna, Yuji, Higuruma) are no creatures of this mod any more: their skills become techniques for players.

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

    // ---- techniques (术式)

    /** ds:limitless_technique, "无下限术式" (天上天下，唯我独尊): gives Gojo's skill items. */
    public static final RegistryObject<Item> LIMITLESS_TECHNIQUE =
            reg("limitless_technique", () -> new com.jujutsuenhancement.item.LimitlessTechniqueItem(new Item.Properties().stacksTo(1)));

    /** ds:limitless1 (无限). */
    public static final RegistryObject<Item> LIMITLESS1 =
            reg("limitless1", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless1", new Item.Properties().stacksTo(1)));

    /** ds:limitless2, "术式顺转 苍" (Gojo's Blue). */
    public static final RegistryObject<Item> LIMITLESS2 =
            reg("limitless2", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless2", new Item.Properties().stacksTo(1)));

    /** ds:limitless3, "术式反转 赫" (Gojo's Red). */
    public static final RegistryObject<Item> LIMITLESS3 =
            reg("limitless3", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless3", new Item.Properties().stacksTo(1)));

    /** ds:limitless4 (空间撕扯). */
    public static final RegistryObject<Item> LIMITLESS4 =
            reg("limitless4", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless4", new Item.Properties().stacksTo(1)));

    /** ds:limitless5 (茈). */
    public static final RegistryObject<Item> LIMITLESS5 =
            reg("limitless5", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless5", new Item.Properties().stacksTo(1)));

    /** ds:limitless6 (领域展开). */
    public static final RegistryObject<Item> LIMITLESS6 =
            reg("limitless6", () -> new com.jujutsuenhancement.item.TechniqueItem("limitless6", new Item.Properties().stacksTo(1)));

    /** ds:yuji_technique, "御厨子" (虎杖悠仁). */
    public static final RegistryObject<Item> YUJI_TECHNIQUE =
            reg("yuji_technique", () -> new com.jujutsuenhancement.item.YujiTechniqueItem(new Item.Properties().stacksTo(1)));
    /** ds:black_fists, "虎杖悠仁 - 黑闪战斗风格". */
    public static final RegistryObject<Item> BLACK_FISTS =
            reg("black_fists", () -> new com.jujutsuenhancement.item.TechniqueItem("black_fists", new Item.Properties().stacksTo(1)));
    /** ds:soul_shrine1, "解-灵魂". */
    public static final RegistryObject<Item> SOUL_SHRINE1 =
            reg("soul_shrine1", () -> new com.jujutsuenhancement.item.TechniqueItem("soul_shrine1", new Item.Properties().stacksTo(1)));
    /** ds:soul_shrine2, "领域展开 『伏魔御佛龛』". */
    public static final RegistryObject<Item> SOUL_SHRINE2 =
            reg("soul_shrine2", () -> new com.jujutsuenhancement.item.TechniqueItem("soul_shrine2", new Item.Properties().stacksTo(1)));
    /** ds:blood_manipulation, "赤血操术 [ 未完成 ]" (a placeholder in the addon too). */
    public static final RegistryObject<Item> BLOOD_MANIPULATION =
            reg("blood_manipulation", () -> new BedrockItem(new Item.Properties().stacksTo(1)));

    /** ds:sukuna_technique, "诅咒之王 · 两面宿傩". */
    public static final RegistryObject<Item> SUKUNA_TECHNIQUE =
            reg("sukuna_technique", () -> new com.jujutsuenhancement.item.SukunaTechniqueItem(new Item.Properties().stacksTo(1)));
    /** ds:sukuna1, "解" skills. */
    public static final RegistryObject<Item> SUKUNA1 =
            reg("sukuna1", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna1", new Item.Properties().stacksTo(1)));
    /** ds:sukuna2, "捌" skills. */
    public static final RegistryObject<Item> SUKUNA2 =
            reg("sukuna2", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna2", new Item.Properties().stacksTo(1)));
    /** ds:sukuna5, "咒词吟唱 / 空间斩". */
    public static final RegistryObject<Item> SUKUNA5 =
            reg("sukuna5", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna5", new Item.Properties().stacksTo(1)));
    /** ds:sukuna4, "「竈」 开". */
    public static final RegistryObject<Item> SUKUNA4 =
            reg("sukuna4", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna4", new Item.Properties().stacksTo(1)));
    /** ds:sukuna3, "领域展开 『伏魔御厨子』". */
    public static final RegistryObject<Item> SUKUNA3 =
            reg("sukuna3", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna3", new Item.Properties().stacksTo(1)));
    /** ds:sukuna_fists, "拳法风格". */
    public static final RegistryObject<Item> SUKUNA_FISTS =
            reg("sukuna_fists", () -> new com.jujutsuenhancement.item.TechniqueItem("sukuna_fists", new Item.Properties().stacksTo(1)));
}
