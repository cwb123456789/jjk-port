package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.FlyheadEntity;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import com.jujutsuenhancement.entity.SukunaAwakenedEntity;
import com.jujutsuenhancement.entity.HigurumaAwakenedEntity;
import com.jujutsuenhancement.entity.YujiAwakenedEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FLYHEAD.get(), FlyheadEntity.createAttributes().build());
        event.put(ModEntities.GOJO_AWAKENED.get(), GojoAwakenedEntity.createAttributes().build());
        event.put(ModEntities.MAHITO_AWAKENED.get(), MahitoAwakenedEntity.createAttributes().build());
        event.put(ModEntities.SUKUNA_AWAKENED.get(), SukunaAwakenedEntity.createAttributes().build());
        event.put(ModEntities.YUJI_AWAKENED.get(), YujiAwakenedEntity.createAttributes().build());
        event.put(ModEntities.HIGURUMA_AWAKENED.get(), HigurumaAwakenedEntity.createAttributes().build());
    }

    /** spawn_rules/flyheads.json: on the surface, any brightness (the addon's brightness filter is 0..15). */
    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.FLYHEAD.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.FLYHEAD_SPAWN_EGG);
        }
    }
}
