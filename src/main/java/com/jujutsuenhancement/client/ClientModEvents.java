package com.jujutsuenhancement.client;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ClientModEvents() {}

    // Disabled: the original JujutsuCraft mod is left untouched.
    public static void attachNativeMahitoModel(EntityRenderersEvent.AddLayers event) {
        EntityType<?> registeredType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("jujutsucraft", "mahito"));
        if (registeredType == null) {
            LOGGER.debug("JujutsuCraft Mahito entity type not found; skipping phase-two renderer adapter");
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>) registeredType;
        var renderer = event.getRenderer(entityType);
        Object rendererObject = renderer;
        if (!(rendererObject instanceof GeoEntityRenderer<?> geoRenderer)) {
            LOGGER.debug("JujutsuCraft Mahito renderer is not a GeckoLib GeoEntityRenderer; skipping phase-two adapter");
            return;
        }
        try {
            Class<?> current = geoRenderer.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    // Never mutate static shared models or final fields: those can be
                    // shared by every entity renderer and may fail under newer runtimes.
                    int modifiers = field.getModifiers();
                    if (GeoModel.class.isAssignableFrom(field.getType())
                            && !Modifier.isStatic(modifiers)
                            && !Modifier.isFinal(modifiers)) {
                        field.setAccessible(true);
                        Object previous = field.get(geoRenderer);
                        if (previous == null || !field.getType().isInstance(previous)) {
                            continue;
                        }
                        field.set(geoRenderer, new com.jujutsuenhancement.client.model.NativeMahitoPhase2GeoModel());
                        LOGGER.info("Attached phase-two geometry adapter to native Mahito renderer field {}", field.getName());
                        return;
                    }
                }
                current = current.getSuperclass();
            }
            LOGGER.warn("Could not locate GeckoLib model field on native Mahito renderer; retaining original renderer model");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Could not attach phase-two geometry adapter; retaining original Mahito renderer model: {}", exception.toString());
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The purple is drawn entirely by particles; without any registered renderer the
        // client crashes the moment the entity enters view.
        event.registerEntityRenderer(ModEntities.HOLLOW_PURPLE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.GOJO_AWAKENED.get(), GojoAwakenedRenderer::new);
        event.registerEntityRenderer(ModEntities.MAHITO_AWAKENED.get(), MahitoAwakenedRenderer::new);
        event.registerEntityRenderer(ModEntities.SUKUNA_AWAKENED.get(), SukunaAwakenedRenderer::new);
        event.registerEntityRenderer(ModEntities.YUJI_AWAKENED.get(), YujiAwakenedRenderer::new);
        event.registerEntityRenderer(ModEntities.MALEVOLENT_SHRINE.get(), DomainShrineRenderer::new);
        event.registerEntityRenderer(ModEntities.SUKUNA_FLAME_ARROW.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.HEIAN_SUKUNA.get(), HeianSukunaRenderer::new);
        event.registerEntityRenderer(ModEntities.MAHITO_PROTOTYPE.get(), MahitoPrototypeRenderer::new);
        event.registerEntityRenderer(ModEntities.MAHITO_DOMAIN_HANDS.get(), MahitoDomainHandsRenderer::new);
        event.registerEntityRenderer(ModEntities.MAHITO_DOMAIN_BARRIER.get(), MahitoDomainBarrierRenderer::new);
    }
}
