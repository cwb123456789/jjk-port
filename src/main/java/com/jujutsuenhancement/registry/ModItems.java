package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JujutsuEnhancement.MODID);

    public static final RegistryObject<Item> GOJO_AWAKENED_SPAWN_EGG =
            ITEMS.register("gojo_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.GOJO_AWAKENED, 0xEFEFEF, 0x2A5BD7, new Item.Properties()));

    public static final RegistryObject<Item> MAHITO_AWAKENED_SPAWN_EGG =
            ITEMS.register("mahito_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.MAHITO_AWAKENED, 0x7C8FB5, 0x3B2A52, new Item.Properties()));

    public static final RegistryObject<Item> SUKUNA_AWAKENED_SPAWN_EGG =
            ITEMS.register("sukuna_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.SUKUNA_AWAKENED, 0x8B1A1A, 0x111111, new Item.Properties()));

    public static final RegistryObject<Item> YUJI_AWAKENED_SPAWN_EGG =
            ITEMS.register("yuji_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.YUJI_AWAKENED, 0xE8A0B0, 0x222222, new Item.Properties()));

    public static final RegistryObject<Item> HIGURUMA_AWAKENED_SPAWN_EGG =
            ITEMS.register("higuruma_awakened_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.HIGURUMA_AWAKENED, 0x3A3A44, 0xC9A227, new Item.Properties()));

    private ModItems() {}
}
