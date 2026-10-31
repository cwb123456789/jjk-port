package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import com.jujutsuenhancement.entity.SukunaAwakenedEntity;
import com.jujutsuenhancement.entity.HigurumaAwakenedEntity;
import com.jujutsuenhancement.entity.YujiAwakenedEntity;
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
        event.put(ModEntities.SUKUNA_AWAKENED.get(), SukunaAwakenedEntity.createAttributes().build());
        event.put(ModEntities.YUJI_AWAKENED.get(), YujiAwakenedEntity.createAttributes().build());
        event.put(ModEntities.HIGURUMA_AWAKENED.get(), HigurumaAwakenedEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GOJO_AWAKENED_SPAWN_EGG);
            event.accept(ModItems.MAHITO_AWAKENED_SPAWN_EGG);
            event.accept(ModItems.SUKUNA_AWAKENED_SPAWN_EGG);
            event.accept(ModItems.YUJI_AWAKENED_SPAWN_EGG);
            event.accept(ModItems.HIGURUMA_AWAKENED_SPAWN_EGG);
        }
    }
}
