package com.jujutsuenhancement.registry;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.DomainShrineEntity;
import com.jujutsuenhancement.entity.GojoAwakenedEntity;
import com.jujutsuenhancement.entity.MahitoAwakenedEntity;
import com.jujutsuenhancement.entity.SukunaAwakenedEntity;
import com.jujutsuenhancement.entity.YujiAwakenedEntity;
import com.jujutsuenhancement.entity.HeianSukunaEntity;
import com.jujutsuenhancement.entity.HollowPurpleEntity;
import com.jujutsuenhancement.entity.SukunaFlameArrowEntity;
import com.jujutsuenhancement.entity.MahitoPrototypeEntity;
import com.jujutsuenhancement.entity.MahitoDomainHandsEntity;
import com.jujutsuenhancement.entity.MahitoDomainBarrierEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JujutsuEnhancement.MODID);

    public static final RegistryObject<EntityType<DomainShrineEntity>> MALEVOLENT_SHRINE =
            ENTITY_TYPES.register("malevolent_shrine", () -> EntityType.Builder
                    .of(DomainShrineEntity::new, MobCategory.MISC)
                    // The Bedrock shrine geometry spans roughly 28 blocks after pixel-to-block scaling.
                    // A tiny entity AABB would cause aggressive client frustum culling.
                    .sized(32.0F, 30.0F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("malevolent_shrine"));

    public static final RegistryObject<EntityType<SukunaFlameArrowEntity>> SUKUNA_FLAME_ARROW =
            ENTITY_TYPES.register("sukuna_flame_arrow", () -> EntityType.Builder
                    .of(SukunaFlameArrowEntity::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("sukuna_flame_arrow"));

    public static final RegistryObject<EntityType<HeianSukunaEntity>> HEIAN_SUKUNA =
            ENTITY_TYPES.register("heian_sukuna", () -> EntityType.Builder
                    .of(HeianSukunaEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("heian_sukuna"));

    public static final RegistryObject<EntityType<MahitoPrototypeEntity>> MAHITO_PROTOTYPE =
            ENTITY_TYPES.register("mahito_prototype", () -> EntityType.Builder
                    .of(MahitoPrototypeEntity::new, MobCategory.MISC)
                    .sized(0.8F, 2.0F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("mahito_prototype"));

    public static final RegistryObject<EntityType<MahitoDomainHandsEntity>> MAHITO_DOMAIN_HANDS =
            ENTITY_TYPES.register("mahito_domain_hands", () -> EntityType.Builder
                    .of(MahitoDomainHandsEntity::new, MobCategory.MISC)
                    .sized(4.0F, 4.0F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("mahito_domain_hands"));

    public static final RegistryObject<EntityType<MahitoDomainBarrierEntity>> MAHITO_DOMAIN_BARRIER =
            ENTITY_TYPES.register("mahito_domain_barrier", () -> EntityType.Builder
                    .of(MahitoDomainBarrierEntity::new, MobCategory.MISC)
                    .sized(24.0F, 16.0F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("mahito_domain_barrier"));

    public static final RegistryObject<EntityType<HollowPurpleEntity>> HOLLOW_PURPLE =
            ENTITY_TYPES.register("hollow_purple", () -> EntityType.Builder
                    .of(HollowPurpleEntity::new, MobCategory.MISC)
                    .sized(1.4F, 1.4F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("hollow_purple"));

    public static final RegistryObject<EntityType<GojoAwakenedEntity>> GOJO_AWAKENED =
            ENTITY_TYPES.register("gojo_awakened", () -> EntityType.Builder
                    .of(GojoAwakenedEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("gojo_awakened"));

    public static final RegistryObject<EntityType<MahitoAwakenedEntity>> MAHITO_AWAKENED =
            ENTITY_TYPES.register("mahito_awakened", () -> EntityType.Builder
                    .of(MahitoAwakenedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("mahito_awakened"));

    public static final RegistryObject<EntityType<SukunaAwakenedEntity>> SUKUNA_AWAKENED =
            ENTITY_TYPES.register("sukuna_awakened", () -> EntityType.Builder
                    .of(SukunaAwakenedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("sukuna_awakened"));

    public static final RegistryObject<EntityType<YujiAwakenedEntity>> YUJI_AWAKENED =
            ENTITY_TYPES.register("yuji_awakened", () -> EntityType.Builder
                    .of(YujiAwakenedEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("yuji_awakened"));

    private ModEntities() {}
}
