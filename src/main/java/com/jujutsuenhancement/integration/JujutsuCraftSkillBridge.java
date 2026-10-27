package com.jujutsuenhancement.integration;

import com.jujutsuenhancement.JujutsuEnhancement;
import com.jujutsuenhancement.entity.HeianSukunaEntity;
import com.jujutsuenhancement.entity.DomainShrineEntity;
import com.jujutsuenhancement.entity.SukunaFlameArrowEntity;
import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Prototype gameplay input for the Heian Sukuna entity.
 * Normal interaction fires one custom flame-arrow approximation for "灶".
 * Sneak-interaction activates the first playable Malevolent Shrine domain prototype.
 * This custom projectile does not invoke JujutsuCraft's native skill dispatcher.
 */
@Mod.EventBusSubscriber(modid = JujutsuEnhancement.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JujutsuCraftSkillBridge {
    private static final int SINGLE_COOLDOWN_TICKS = 30;
    private static final int DOMAIN_COOLDOWN_TICKS = 1200;

    private JujutsuCraftSkillBridge() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof HeianSukunaEntity)) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;

        // Consume this server-side interaction before any factory, cooldown, or spawn
        // failure path can fall through to vanilla entity interaction behavior.
        event.setCanceled(true);
        long now = player.level().getGameTime();
        HeianSukunaEntity sukuna = (HeianSukunaEntity) event.getTarget();
        if (player.isCrouching()) {
            if (sukuna.isFugaCharger(player.getUUID())) sukuna.cancelFugaCharge();
            if (!sukuna.canUseDomain(now)) {
                event.setCanceled(true);
                return;
            }
            DomainShrineEntity shrine = ModEntities.MALEVOLENT_SHRINE.get().create(player.level());
            if (shrine == null) return;
            Entity target = event.getTarget();
            shrine.setPos(target.getX(), target.getY(), target.getZ());
            shrine.setOwner(player);
            if (player.serverLevel().addFreshEntity(shrine)) {
                sukuna.commitDomain(now, DOMAIN_COOLDOWN_TICKS);
                player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 0.65F);
                player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                        target.getX(), target.getY() + 1.0D, target.getZ(),
                        80, 5.0D, 2.0D, 5.0D, 0.15D);
                event.setCanceled(true);
            }
            return;
        }

        if (!sukuna.canUseBasicAttack(now)) {
            event.setCanceled(true);
            return;
        }

        if (!sukuna.isFugaCharging()) {
            sukuna.startFugaCharge(player.getUUID());
            Vec3 chargeOrigin = new Vec3(sukuna.getX(),
                    sukuna.getY() + sukuna.getBbHeight() * 0.65D, sukuna.getZ());
            player.serverLevel().playSound(null, chargeOrigin.x, chargeOrigin.y, chargeOrigin.z,
                    SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8F, 0.45F);
            player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    chargeOrigin.x, chargeOrigin.y, chargeOrigin.z,
                    18, 0.28D, 0.35D, 0.28D, 0.015D);
            event.setCanceled(true);
            return;
        }

        // The player who started the charge must release it; other players cannot steal it.
        if (!sukuna.isFugaCharger(player.getUUID()) || !sukuna.isFugaReady()) {
            event.setCanceled(true);
            return;
        }

        // The flame should visibly originate from Sukuna, not from the interacting player's camera.
        Vec3 origin = new Vec3(sukuna.getX(),
                sukuna.getY() + sukuna.getBbHeight() * 0.65D, sukuna.getZ());
        Vec3 aimPoint = player.getEyePosition().add(player.getLookAngle().normalize().scale(24.0D));
        Vec3 direction = aimPoint.subtract(origin);
        if (direction.lengthSqr() < 1.0E-6D) direction = player.getLookAngle();
        direction = direction.normalize();
        SukunaFlameArrowEntity arrow = ModEntities.SUKUNA_FLAME_ARROW.get().create(player.level());
        if (arrow == null) {
            event.setCanceled(true);
            return;
        }
        arrow.setPos(origin.x, origin.y, origin.z);
        // Attribute damage and allied-entity filtering to Sukuna, not the player who aims the attack.
        // Keep the charger excluded from collision as well.
        arrow.launch(sukuna, player, direction);
        if (player.serverLevel().addFreshEntity(arrow)) {
            sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);
            player.serverLevel().playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F,
                    0.65F + player.getRandom().nextFloat() * 0.15F);
            event.setCanceled(true);
        }
        // If the server rejects the spawn, leave the charge ready so the player can retry.
    }


}
