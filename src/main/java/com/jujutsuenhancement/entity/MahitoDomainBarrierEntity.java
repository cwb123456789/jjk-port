package com.jujutsuenhancement.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Approximate cube-panel domain wall, ported without Bedrock poly_mesh data. */
public final class MahitoDomainBarrierEntity extends Entity implements GeoEntity {
    private static final int LIFETIME_TICKS = 60;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int age;

    public MahitoDomainBarrierEntity(EntityType<? extends MahitoDomainBarrierEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && ++age >= LIFETIME_TICKS) discard();
    }

    /** Restores the elapsed lifetime when rebuilding an effect during an active domain intro. */
    public void restoreAge(int restoredAge) { age = Math.max(0, Math.min(LIFETIME_TICKS, restoredAge)); }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { age = Math.max(0, Math.min(LIFETIME_TICKS, tag.getInt("BarrierAge"))); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("BarrierAge", age); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "static_barrier", 0, state -> PlayState.STOP));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
