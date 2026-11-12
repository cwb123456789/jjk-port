package com.jujutsuenhancement.entity;

import com.jujutsuenhancement.registry.ModEntities;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Awakened Mahito: standalone mob using the Bedrock "Jujutsu Awakening" animations.
 * Forms: base (basic combo), blade, heavy. At half health he performs the
 * "Instant Spirit Body of Distorted Killing" transformation (stage 2, Bedrock skin and
 * body/human techniques). The Self-Embodiment-of-Perfection domain starts 0.2 s after casting.
 */
public final class MahitoAwakenedEntity extends PathfinderMob implements GeoEntity, TestCastable, ClashCaster {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(MahitoAwakenedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FORM =
            SynchedEntityData.defineId(MahitoAwakenedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STAGE2 =
            SynchedEntityData.defineId(MahitoAwakenedEntity.class, EntityDataSerializers.BOOLEAN);

    private static final UUID STAGE2_BOOST = UUID.fromString("5b7a1c2e-6f64-4f55-9a53-3a0d4f7e2c11");
    private static final Map<String, RawAnimation> ONCE = new HashMap<>();
    private static final Map<String, RawAnimation> LOOP = new HashMap<>();

    private static RawAnimation once(String name) {
        return ONCE.computeIfAbsent(name, n -> RawAnimation.begin().thenPlay("animation.mahito." + n));
    }

    private static RawAnimation loop(String name) {
        return LOOP.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop("animation.mahito." + n));
    }

    /** One melee move: animation name, length, tick of impact, damage multiplier, reach, knockback. */
    private record Move(String anim, int ticks, int hit, float damage, double reach, double knockback) {}

    private static final Move[] BASE = {
            new Move("basic1", 24, 10, 1.0F, 3.2D, 0.4D),
            new Move("basic2", 25, 11, 1.0F, 3.2D, 0.4D),
            new Move("basic3", 15, 6, 1.0F, 3.2D, 0.3D),
            new Move("basic4", 20, 8, 1.5F, 3.4D, 0.9D)};
    private static final Move[] BLADE = {
            new Move("blade_attack1", 15, 6, 1.1F, 3.6D, 0.3D),
            new Move("blade_attack2", 15, 6, 1.1F, 3.6D, 0.3D),
            new Move("blade_attack3", 20, 8, 1.2F, 3.8D, 0.4D),
            new Move("blade_attack4", 20, 8, 1.2F, 3.8D, 0.4D),
            new Move("blade_attack5", 25, 10, 1.6F, 4.2D, 1.0D)};
    private static final Move[] BLADE_SKILL = {
            new Move("blade_ab1", 25, 12, 2.0F, 5.0D, 0.8D),
            new Move("blade_ab2", 25, 12, 2.0F, 5.0D, 0.8D)};
    private static final Move[] HEAVY = {
            new Move("heavy_attack1", 30, 14, 1.4F, 3.6D, 1.0D),
            new Move("heavy_attack2", 30, 14, 1.4F, 3.6D, 1.0D),
            new Move("heavy_attack3", 30, 14, 1.4F, 3.6D, 1.0D),
            new Move("heavy_attack4", 30, 14, 1.6F, 3.8D, 1.3D)};
    private static final Move[] HEAVY_SKILL = {
            new Move("heavy_ab1", 30, 16, 2.2F, 5.0D, 1.5D),
            new Move("heavy_ab2", 51, 26, 2.8F, 5.5D, 1.8D)};
    private static final Move[] STAGE2_COMBO = {
            new Move("attack1", 24, 10, 1.4F, 3.6D, 0.5D),
            new Move("attack2", 24, 10, 1.4F, 3.6D, 0.5D),
            new Move("attack3", 25, 11, 1.5F, 3.8D, 0.6D),
            new Move("attack4", 25, 11, 1.5F, 3.8D, 0.6D),
            new Move("attack5", 25, 11, 2.0F, 4.0D, 1.2D)};
    private static final Move[] STAGE2_SKILL = {
            new Move("body_ab1", 30, 18, 2.2F, 5.0D, 1.2D),
            new Move("body_ab2", 41, 22, 2.4F, 5.5D, 1.2D),
            new Move("human_ab1", 30, 20, 2.2F, 6.0D, 1.0D),
            new Move("human_ab2", 30, 18, 2.2F, 6.0D, 1.0D),
            new Move("body_ab4", 70, 42, 3.2F, 7.0D, 2.0D)};

    private static final java.util.Map<String, Move> MOVES = new java.util.LinkedHashMap<>();
    static {
        for (Move[] group : new Move[][] {BASE, BLADE, BLADE_SKILL, HEAVY, HEAVY_SKILL, STAGE2_COMBO, STAGE2_SKILL}) {
            for (Move m : group) MOVES.put(m.anim(), m);
        }
    }

    private static final int STAGE2_TICKS = 110;
    private static final int DOMAIN_START = 4;       // 0.2 s
    private static final int DOMAIN_INTRO = 60;
    private static final int DOMAIN_TOTAL = 200;
    private static final double DOMAIN_RADIUS = 24.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Move move;
    private int moveTick;
    private boolean moveHit;
    private int comboIndex;
    private long nextMoveTime;
    private long formSwitchTime;
    private long nextDomainTime;
    private int stage2Tick = -1;
    private boolean stage2Done;
    private int domainTick = -1;
    private boolean clashHold;
    private UUID handsUuid;
    private UUID barrierUuid;

    public MahitoAwakenedEntity(EntityType<? extends MahitoAwakenedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public boolean isStage2() { return entityData.get(STAGE2); }

    private boolean executed;

    public boolean isExecuted() { return executed; }

    /** Held in place (dying pose) while Yuji's execution plays. */
    public void setExecuted(boolean value) {
        executed = value;
        move = null;
        domainTick = -1;
        if (level() instanceof ServerLevel s) discardVisuals(s);
        entityData.set(ANIM, value ? "dying" : "");
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ChaseGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> (e instanceof Player p && !p.isCreative() && !p.isSpectator()) || e instanceof GojoAwakenedEntity || e instanceof YujiAwakenedEntity || e instanceof HigurumaAwakenedEntity));
    }

    private boolean busy() {
        return move != null || stage2Tick >= 0 || domainTick >= 0;
    }

    private static final class ChaseGoal extends Goal {
        private final MahitoAwakenedEntity mob;
        ChaseGoal(MahitoAwakenedEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (mob.distanceToSqr(t) > 2.8D * 2.8D) mob.getNavigation().moveTo(t, 1.2D);
            else mob.getNavigation().stop();
        }
    }

    // ----------------------------------------------------------- behaviour

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && level() instanceof ServerLevel server) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (executed) { freeze(); return; }
        if (clashHold) { freeze(); return; }
        DomainClashEntity.check(this);
        if (stage2Tick >= 0) { runStage2(level); return; }
        if (domainTick >= 0) { runDomain(level); return; }
        if (move != null) { runMove(level); return; }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (!stage2Done && getHealth() <= getMaxHealth() * 0.5F) {
            startStage2();
            return;
        }
        double dist = distanceTo(target);
        if (now >= nextDomainTime && dist <= 16.0D && (getHealth() < getMaxHealth() * 0.75F || random.nextInt(200) == 0)) {
            startDomain();
            return;
        }
        if (!isStage2() && now >= formSwitchTime) {
            entityData.set(FORM, random.nextInt(3));
            formSwitchTime = now + 300 + random.nextInt(200);
        }
        if (dist < 3.6D && now >= nextMoveTime) startMove(pickMove());
    }

    private Move pickMove() {
        boolean skill = random.nextInt(4) == 0;
        if (isStage2()) {
            if (skill) return STAGE2_SKILL[random.nextInt(STAGE2_SKILL.length)];
            return STAGE2_COMBO[comboIndex++ % STAGE2_COMBO.length];
        }
        return switch (entityData.get(FORM)) {
            case 1 -> skill ? BLADE_SKILL[random.nextInt(BLADE_SKILL.length)] : BLADE[comboIndex++ % BLADE.length];
            case 2 -> skill ? HEAVY_SKILL[random.nextInt(HEAVY_SKILL.length)] : HEAVY[comboIndex++ % HEAVY.length];
            default -> BASE[comboIndex++ % BASE.length];
        };
    }

    private void startMove(Move m) {
        move = m;
        moveTick = 0;
        moveHit = false;
        getNavigation().stop();
        entityData.set(ANIM, m.anim());
    }

    private void endMove(int cooldown) {
        move = null;
        moveTick = 0;
        nextMoveTime = level().getGameTime() + cooldown;
        entityData.set(ANIM, "");
    }

    private void freeze() {
        getNavigation().stop();
        Vec3 d = getDeltaMovement();
        setDeltaMovement(0.0D, Math.min(0.0D, d.y), 0.0D);
    }

    private void face(Entity t) {
        double dx = t.getX() - getX();
        double dz = t.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private boolean isFoe(Entity e) {
        if (!(e instanceof LivingEntity) || e == this || e instanceof MahitoAwakenedEntity) return false;
        if (e == getTarget()) return true;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof GojoAwakenedEntity || e instanceof YujiAwakenedEntity || e instanceof HigurumaAwakenedEntity || e == getTarget();
    }

    private void runMove(ServerLevel level) {
        moveTick++;
        freeze();
        LivingEntity t = getTarget();
        if (t != null && moveTick < move.hit()) face(t);
        if (moveTick == move.hit() && !moveHit) {
            moveHit = true;
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damage();
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(move.reach()), this::isFoe)) {
                Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(look) < 0.2D) continue;
                e.hurt(damageSources().mobAttack(this), dmg);
                Vec3 push = to.lengthSqr() > 1.0E-4D ? to.normalize().scale(move.knockback()) : Vec3.ZERO;
                e.push(push.x, 0.2D + move.knockback() * 0.1D, push.z);
                level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.1D);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.1F, 0.7F);
        }
        if (moveTick >= move.ticks()) endMove(isStage2() ? 6 : 10);
    }

    // ---- Instant Spirit Body of Distorted Killing (stage 2)

    private void startStage2() {
        stage2Done = true;
        stage2Tick = 0;
        move = null;
        getNavigation().stop();
        entityData.set(ANIM, "stage2");
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void runStage2(ServerLevel level) {
        stage2Tick++;
        freeze();
        if (stage2Tick == 4) {
            entityData.set(STAGE2, true); // skin switches at 0.2 s
        }
        if (stage2Tick % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 6, 0.6D, 0.9D, 0.6D, 0.05D);
        }
        if (stage2Tick >= STAGE2_TICKS) {
            stage2Tick = -1;
            entityData.set(ANIM, "");
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            var attack = getAttribute(Attributes.ATTACK_DAMAGE);
            if (speed != null && speed.getModifier(STAGE2_BOOST) == null) {
                speed.addPermanentModifier(new AttributeModifier(STAGE2_BOOST, "stage2", 0.25D, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (attack != null && attack.getModifier(STAGE2_BOOST) == null) {
                attack.addPermanentModifier(new AttributeModifier(STAGE2_BOOST, "stage2", 0.6D, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            heal(getMaxHealth() * 0.25F);
            nextMoveTime = level.getGameTime() + 10;
        }
    }

    // ---- Self-Embodiment of Perfection (domain), takes effect 0.2 s after cast

    private void startDomain() {
        domainTick = 0;
        move = null;
        getNavigation().stop();
        entityData.set(ANIM, "2s_domain_start");
    }

    private void runDomain(ServerLevel level) {
        domainTick++;
        freeze();
        if (domainTick == DOMAIN_START) {
            spawnVisuals(level);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0F, 0.6F);
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 120, 2.5D, 1.5D, 2.5D, 0.1D);
        }
        if (domainTick == DOMAIN_INTRO) entityData.set(ANIM, "2s_domain");
        if (domainTick >= DOMAIN_START && domainTick < DOMAIN_TOTAL) {
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(DOMAIN_RADIUS),
                    x -> isFoe(x) && x.distanceToSqr(this) <= DOMAIN_RADIUS * DOMAIN_RADIUS)) {
                LivingEntity living = (LivingEntity) e;
                if (domainTick % 10 == 0 || domainTick == DOMAIN_START) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 3, false, false));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 14, 2, false, false));
                }
                if (domainTick % 20 == 0) {
                    living.hurt(damageSources().indirectMagic(this, this), isStage2() ? 6.0F : 4.0F);
                    level.sendParticles(ParticleTypes.SOUL, living.getX(), living.getY() + 1.0D, living.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.05D);
                }
            }
        }
        if (domainTick >= DOMAIN_TOTAL) {
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(DOMAIN_RADIUS),
                    x -> isFoe(x) && x.distanceToSqr(this) <= DOMAIN_RADIUS * DOMAIN_RADIUS)) {
                e.hurt(damageSources().indirectMagic(this, this), 14.0F);
            }
            discardVisuals(level);
            domainTick = -1;
            entityData.set(ANIM, "");
            nextDomainTime = level.getGameTime() + 2400;
            nextMoveTime = level.getGameTime() + 20;
        }
    }

    // ------------------------------------------------------------------ domain clash

    @Override public ClashKind clashKind() { return ClashKind.PERFECTION; }

    @Override
    public int clashCastTicks() {
        if (domainTick < DOMAIN_START) return -1;
        int c = domainTick - DOMAIN_START + 1;
        return c > 70 ? 1000 : c;
    }

    @Override
    public void clashBegin() {
        if (!(level() instanceof ServerLevel server)) return;
        discardVisuals(server);
        domainTick = -1;
        entityData.set(ANIM, "");
        nextDomainTime = Math.max(nextDomainTime, server.getGameTime() + 2400);
        nextMoveTime = server.getGameTime() + 20;
    }

    @Override public void clashHold(boolean hold) { this.clashHold = hold; }

    /** Self-Embodiment of Perfection won: the domain is cast again where Mahito stands. */
    @Override
    public void clashWin(DomainClashEntity clash) {
        startDomain();
        domainTick = DOMAIN_START - 1;
    }

    @Override public void clashLose() { }

    private void spawnVisuals(ServerLevel level) {
        var hands = ModEntities.MAHITO_DOMAIN_HANDS.get().create(level);
        if (hands != null) {
            hands.moveTo(getX(), getY() + 0.2D, getZ(), getYRot(), 0.0F);
            if (level.addFreshEntity(hands)) handsUuid = hands.getUUID();
        }
        var barrier = ModEntities.MAHITO_DOMAIN_BARRIER.get().create(level);
        if (barrier != null) {
            barrier.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
            if (level.addFreshEntity(barrier)) barrierUuid = barrier.getUUID();
        }
    }

    private void discardVisuals(ServerLevel level) {
        for (UUID id : new UUID[] {handsUuid, barrierUuid}) {
            if (id == null) continue;
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        handsUuid = null;
        barrierUuid = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && level() instanceof ServerLevel server) {
            discardVisuals(server);
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (stage2Tick >= 0 || executed) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public java.util.List<String> castableSkills() {
        java.util.List<String> names = new java.util.ArrayList<>(MOVES.keySet());
        names.add("stage2");
        names.add("domain");
        return names;
    }

    @Override
    public boolean cast(String name, LivingEntity target) {
        if (busy() || executed || !(level() instanceof ServerLevel)) return false;
        switch (name) {
            case "stage2" -> startStage2();
            case "domain" -> startDomain();
            default -> {
                Move m = MOVES.get(name);
                if (m == null) return false;
                startMove(m);
            }
        }
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return TestCastable.interact(this, player, hand);
    }


    // --------------------------------------------------------- persistence

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, "");
        entityData.define(FORM, 0);
        entityData.define(STAGE2, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Stage2", isStage2());
        tag.putBoolean("Stage2Done", stage2Done);
        tag.putInt("Form", entityData.get(FORM));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stage2Done = tag.getBoolean("Stage2Done");
        entityData.set(STAGE2, tag.getBoolean("Stage2"));
        entityData.set(FORM, Math.floorMod(tag.getInt("Form"), 3));
    }

    // ------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            String anim = entityData.get(ANIM);
            if (!anim.isEmpty()) return state.setAndContinue(anim.equals("dying") ? loop(anim) : once(anim));
            boolean moving = state.isMoving();
            if (isStage2()) return state.setAndContinue(loop("2s_domain_idle"));
            return switch (entityData.get(FORM)) {
                case 1 -> state.setAndContinue(moving ? loop("blades_sprint") : loop("blades_idle"));
                case 2 -> state.setAndContinue(moving ? loop("heavy_walk") : loop("heavy_idle"));
                default -> state.setAndContinue(loop("domain_idle"));
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
