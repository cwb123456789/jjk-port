package com.jujutsuenhancement.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Awakened Yuji: martial artist with Black Flash. Hits roll for a Black Flash; consecutive
 * Black Flashes build a streak (higher chance, higher damage). Executes a weakened Mahito
 * with the Bedrock "enraged Yuji vs Mahito" sequence.
 */
public final class YujiAwakenedEntity extends PathfinderMob implements GeoEntity, TestCastable {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(YujiAwakenedEntity.class, EntityDataSerializers.STRING);
    private static final Map<String, RawAnimation> CACHE = new HashMap<>();
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.04F, 0.0F, 0.06F), 2.0F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9F, 0.05F, 0.1F), 1.6F);

    private static RawAnimation anim(String k) {
        return CACHE.computeIfAbsent(k, n -> n.equals("idle") || n.equals("walk")
                ? RawAnimation.begin().thenLoop("animation.yuji." + n)
                : RawAnimation.begin().thenPlay("animation.yuji." + n));
    }

    private record Move(String anim, int ticks, int hit, float damage, double reach, double knockback) {}

    private static final Move[] COMBO = {
            new Move("attack1", 20, 8, 1.0F, 3.0D, 0.3D), new Move("attack2", 20, 8, 1.0F, 3.0D, 0.3D),
            new Move("attack3", 20, 8, 1.0F, 3.0D, 0.3D), new Move("attack4", 20, 8, 1.1F, 3.0D, 0.4D),
            new Move("attack5", 20, 8, 1.1F, 3.0D, 0.4D), new Move("attack6", 20, 9, 1.6F, 3.2D, 1.0D)};
    private static final Move[] SKILLS = {
            new Move("manji_kick", 15, 7, 1.5F, 3.4D, 0.9D), new Move("manji_kick2", 20, 10, 1.7F, 3.4D, 1.0D),
            new Move("triple_kick", 15, 7, 1.6F, 3.4D, 0.8D), new Move("triple_kick2", 26, 12, 2.0F, 3.6D, 1.1D),
            new Move("fierce_rush", 20, 9, 1.8F, 3.8D, 1.0D), new Move("fierce_rush2", 32, 16, 2.2F, 4.0D, 1.4D),
            new Move("divergent_blow", 26, 12, 2.4F, 3.6D, 1.5D)};

    private static final int EXECUTE_TICKS = 156;
    private static final int EXECUTE_HIT = 120;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Move move;
    private int moveTick;
    private boolean moveHit;
    private int comboIndex;
    private int streak;
    private long nextMove;
    private int executeTick = -1;
    private MahitoAwakenedEntity executeVictim;

    public YujiAwakenedEntity(EntityType<? extends YujiAwakenedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 350.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.38D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    private static boolean isCurse(LivingEntity e) {
        if (e instanceof Player || e instanceof YujiAwakenedEntity || e instanceof GojoAwakenedEntity) return false;
        if (e instanceof Monster) return true;
        String n = e.getClass().getSimpleName();
        return n.startsWith("Sukuna") || n.startsWith("Mahito") || n.startsWith("Cursed") || n.startsWith("Jogo")
                || n.startsWith("Choso") || n.startsWith("Hanami");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && isCurse(e)));
    }

    private boolean busy() { return move != null || executeTick >= 0; }

    private static final class ChaseGoal extends Goal {
        private final YujiAwakenedEntity mob;
        ChaseGoal(YujiAwakenedEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (mob.distanceToSqr(t) > 2.4D * 2.4D) mob.getNavigation().moveTo(t, 1.3D);
            else mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && level() instanceof ServerLevel server) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        if (executeTick >= 0) { runExecution(level); return; }
        if (move != null) { runMove(level); return; }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        if (t instanceof MahitoAwakenedEntity m && m.isStage2() && m.getHealth() <= m.getMaxHealth() * 0.35F
                && distanceToSqr(t) < 10.0D * 10.0D && !m.isExecuted()) {
            startExecution(m);
            return;
        }
        if (distanceTo(t) < 3.2D && level.getGameTime() >= nextMove) {
            Move m = random.nextInt(4) == 0 ? SKILLS[random.nextInt(SKILLS.length)] : COMBO[comboIndex++ % COMBO.length];
            move = m;
            moveTick = 0;
            moveHit = false;
            getNavigation().stop();
            entityData.set(ANIM, m.anim());
        }
    }

    private void freeze() {
        getNavigation().stop();
        Vec3 d = getDeltaMovement();
        setDeltaMovement(0.0D, Math.min(0.0D, d.y), 0.0D);
    }

    private void face(Entity t) {
        float yaw = (float) (Math.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void runMove(ServerLevel level) {
        moveTick++;
        freeze();
        LivingEntity t = getTarget();
        if (t != null && moveTick < move.hit()) face(t);
        if (moveTick == move.hit() && !moveHit) {
            moveHit = true;
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            boolean landed = false;
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(move.reach()),
                    x -> x instanceof LivingEntity le && (isCurse(le) || le == getTarget()))) {
                Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(look) < 0.2D) continue;
                landed = true;
                float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damage();
                double kb = move.knockback();
                boolean flash = random.nextFloat() < Math.min(0.45F, 0.10F + 0.07F * streak);
                if (flash) {
                    dmg *= 2.5F + 0.5F * streak;
                    kb += 1.2D;
                    blackFlashFx(level, e);
                }
                e.hurt(damageSources().mobAttack(this), dmg);
                Vec3 push = to.lengthSqr() > 1.0E-4D ? to.normalize().scale(kb) : Vec3.ZERO;
                e.push(push.x, 0.25D + kb * 0.1D, push.z);
                if (flash) streak++; else streak = 0;
            }
            if (!landed) streak = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        if (moveTick >= move.ticks()) {
            move = null;
            nextMove = level.getGameTime() + 4;
            entityData.set(ANIM, "");
        }
    }

    /** Black Flash: dark lightning, red sparks, loud impact. */
    private void blackFlashFx(ServerLevel level, Entity e) {
        double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.6D, z = e.getZ();
        level.sendParticles(BLACK, x, y, z, 60, 0.7D, 0.7D, 0.7D, 0.05D);
        level.sendParticles(RED, x, y, z, 40, 0.8D, 0.8D, 0.8D, 0.25D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 50, 0.9D, 0.9D, 0.9D, 0.4D);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5F, 1.6F);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.2F);
    }

    // ---- execution of Mahito

    private void startExecution(MahitoAwakenedEntity m) {
        executeVictim = m;
        executeTick = 0;
        move = null;
        m.setExecuted(true);
        Vec3 dir = m.position().subtract(position()).multiply(1, 0, 1);
        if (dir.lengthSqr() > 1.0E-4D) {
            Vec3 spot = m.position().subtract(dir.normalize().scale(1.6D));
            moveTo(spot.x, m.getY(), spot.z, getYRot(), 0.0F);
        }
        face(m);
        entityData.set(ANIM, "execute_mahito");
    }

    private void runExecution(ServerLevel level) {
        executeTick++;
        freeze();
        if (executeVictim == null || !executeVictim.isAlive()) { abortExecution(); return; }
        face(executeVictim);
        if (executeTick % 12 == 0) {
            level.sendParticles(RED, getX(), getY() + 1.2D, getZ(), 8, 0.4D, 0.6D, 0.4D, 0.05D);
        }
        if (executeTick == EXECUTE_HIT) {
            blackFlashFx(level, executeVictim);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, executeVictim.getX(), executeVictim.getY() + 1.0D, executeVictim.getZ(), 1, 0, 0, 0, 0);
            executeVictim.setExecuted(false);
            executeVictim.hurt(damageSources().genericKill(), Float.MAX_VALUE);
            executeVictim.kill();
        }
        if (executeTick >= EXECUTE_TICKS) abortExecution();
    }

    private void abortExecution() {
        if (executeVictim != null && executeVictim.isAlive()) executeVictim.setExecuted(false);
        executeVictim = null;
        executeTick = -1;
        streak = 0;
        entityData.set(ANIM, "");
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (executeVictim != null && executeVictim.isAlive()) executeVictim.setExecuted(false);
        super.remove(reason);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (executeTick >= 0) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public java.util.List<String> castableSkills() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Move m : COMBO) names.add(m.anim());
        for (Move m : SKILLS) names.add(m.anim());
        return names;
    }

    @Override
    public boolean cast(String name, LivingEntity target) {
        if (busy() || !(level() instanceof ServerLevel)) return false;
        for (Move[] group : new Move[][] {COMBO, SKILLS}) {
            for (Move m : group) {
                if (m.anim().equals(name)) {
                    move = m;
                    moveTick = 0;
                    moveHit = false;
                    getNavigation().stop();
                    entityData.set(ANIM, m.anim());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return TestCastable.interact(this, player, hand);
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, "");
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> {
            String a = entityData.get(ANIM);
            if (!a.isEmpty()) return state.setAndContinue(anim(a));
            return state.setAndContinue(anim(state.isMoving() ? "walk" : "idle"));
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
