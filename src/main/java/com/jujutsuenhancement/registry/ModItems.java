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

    private ModItems() {}
}
