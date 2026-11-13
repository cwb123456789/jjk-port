package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * A one-shot visual: carries the name of an effect set (particles from the Bedrock pack + sounds, see
 * {@code client.bedrock.FxShow}) to the clients near it, which play it when the entity first appears.
 * It stays for three ticks and is never saved. This is how the server tells clients "play this Bedrock particle here".
 */
public final class BedrockFxEntity extends Entity {
    private static final EntityDataAccessor<String> FX =
            SynchedEntityData.defineId(BedrockFxEntity.class, EntityDataSerializers.STRING);

    private int life;

    public BedrockFxEntity(EntityType<? extends BedrockFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Plays the effect set {@code fx} at the position, turned by {@code yaw} (Minecraft degrees). */
    public static void play(ServerLevel level, String fx, double x, double y, double z, float yaw) {
        BedrockFxEntity e = ModEntities.BEDROCK_FX.get().create(level);
        if (e == null) return;
        e.moveTo(x, y, z, yaw, 0.0F);
        e.entityData.set(FX, fx);
        level.addFreshEntity(e);
    }

    public String getFx() {
        return entityData.get(FX);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && ++life > 3) discard();
    }

    @Override protected void defineSynchedData() {
        entityData.define(FX, "");
    }

    @Override public boolean shouldBeSaved() { return false; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
