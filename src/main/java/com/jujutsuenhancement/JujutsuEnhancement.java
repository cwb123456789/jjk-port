package com.jujutsuenhancement;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.jujutsuenhancement.registry.ModEntities;
import com.jujutsuenhancement.registry.ModItems;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(JujutsuEnhancement.MODID)
public final class JujutsuEnhancement {
    public static final String MODID = "jujutsuenhancement";
    private static final Logger LOGGER = LogUtils.getLogger();

    public JujutsuEnhancement() {
        ModEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("[JujutsuEnhancement] Loaded version 0.1.0");
        LOGGER.info("[JujutsuEnhancement] JujutsuCraft detected: {}",
                ModList.get().isLoaded("jujutsucraft"));
    }
}
