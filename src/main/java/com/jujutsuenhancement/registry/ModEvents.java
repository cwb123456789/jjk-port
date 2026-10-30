package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GOJO_AWAKENED.get(), GojoAwakenedEntity.createAttributes().build());
        event.put(ModEntities.MAHITO_AWAKENED.get(), MahitoAwakenedEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GOJO_AWAKENED_SPAWN_EGG);
            event.accept(ModItems.MAHITO_AWAKENED_SPAWN_EGG);
        }
    }
}
