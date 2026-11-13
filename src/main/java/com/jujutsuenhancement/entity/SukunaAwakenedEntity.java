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

/**
 * Awakened Sukuna (possessing Megumi): standalone mob using the Bedrock animations.
 * Skills: combo, Dismantle, Cleave, Fuga "Open" (flame arrow), "Furnace" (flame volley),
 * Malevolent Shrine, and the possession cutscene on first contact.
 */
public final class SukunaAwakenedEntity extends PathfinderMob implements GeoEntity, TestCastable, ClashCaster, DismantleCaster {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(SukunaAwakenedEntity.class, EntityDataSerializers.STRING);

    private static final Map<String, RawAnimation> CACHE = new HashMap<>();

    private static RawAnimation anim(String key) {
        return CACHE.computeIfAbsent(key, k -> switch (k) {
            case "idle", "walk" -> RawAnimation.begin().thenLoop("animation.sukuna." + k);
            case "shrine" -> RawAnimation.begin().thenPlay("animation.sukuna.shrine").thenLoop("animation.sukuna.shrine_hold");
            case "possession" -> RawAnimation.begin().thenPlay("animation.sukuna.possession")
                    .thenPlay("animation.sukuna.possessed_ready");
            case "fuga" -> RawAnimation.begin().thenPlay("animation.sukuna.fuga_activate")
                    .thenLoop("animation.sukuna.fuga_hold").thenPlay("animation.sukuna.fuga_release");
            default -> RawAnimation.begin().thenPlay("animation.sukuna." + k);
        });
    }

    // skill ids
    private static final String[] COMBO = {"attack1", "attack2", "attack3", "attack4", "attack5"};
    private static final int[] COMBO_TICKS = {19, 20, 19, 19, 29};
    private static final int POSSESSION_TICKS = 1202 + 80;
    private static final int FUGA_CHARGE = 55;
    private static final int FUGA_TOTAL = 55 + 6 + 30;
    private static final int FURNACE_TOTAL = 190;
    private static final int SHRINE_TOTAL = 100 + 400;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean clashHold;
    private String skill = "";
    private int skillTick;
    private int comboIndex;
    private boolean possessed;
    private long nextAttack;
    private long nextDismantle;
    private long nextDissect;
    private long nextBack;
    private long nextDown;
    private long nextCleave;
    private long nextFuga;
    private long nextFurnace;
    private long nextShrine;

    public SukunaAwakenedEntity(EntityType<? extends SukunaAwakenedEntity> type, Level level) {
        super(type, level);
        this.xpReward = 80;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

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

    private static final class ChaseGoal extends Goal {
        private final SukunaAwakenedEntity mob;
        ChaseGoal(SukunaAwakenedEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.skill.isEmpty();
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

    private boolean isFoe(Entity e) {
        if (!(e instanceof LivingEntity) || e == this || e instanceof SukunaAwakenedEntity) return false;
        if (e == getTarget()) return true;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof GojoAwakenedEntity || e instanceof YujiAwakenedEntity || e instanceof HigurumaAwakenedEntity || e == getTarget();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && level() instanceof ServerLevel server) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        if (clashHold) {
            getNavigation().stop();
            return;
        }
        DomainClashEntity.check(this);
        if (!skill.isEmpty()) { runSkill(level); return; }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        long now = level.getGameTime();
        if (!possessed) { start("possession"); return; }
        double dist = distanceTo(target);
        if (now >= nextShrine && dist < 20.0D && (getHealth() < getMaxHealth() * 0.6F || random.nextInt(300) == 0)) {
            start("shrine");
        } else if (dist < 3.4D && now >= nextAttack) {
            start(COMBO[comboIndex % COMBO.length]);
        } else if (dist < 3.4D && now >= nextCleave && random.nextInt(60) == 0) {
            start("cleave");
        } else if (dist < 6.5D && now >= nextBack && random.nextInt(40) == 0) {
            start("dismantle_back");
        } else if (dist < 8.0D && now >= nextDown && random.nextInt(160) == 0) {
            start("dismantle_down");
        } else if (dist >= 3.4D && random.nextInt(30) == 0) {
            if (dist > 6.0D && dist < 40.0D && now >= nextDissect && random.nextInt(3) == 0 && hasLineOfSight(target)) start("dissect");
            else if (dist < 30.0D && now >= nextDismantle && hasLineOfSight(target)) start("dismantle");
            else if (dist > 8.0D && now >= nextFuga && hasLineOfSight(target)) start("fuga");
            else if (dist > 6.0D && now >= nextFurnace && hasLineOfSight(target)) start("furnace");
        }
    }

    private void start(String name) {
        skill = name;
        skillTick = 0;
        getNavigation().stop();
        // cooldowns from the addon's click function (sukuna_ab1 50, dissect 150, backwards 150, downwards 480 ticks)
        long now = level().getGameTime();
        switch (name) {
            case "dismantle" -> nextDismantle = now + 50;
            case "dissect" -> nextDissect = now + 150;
            case "dismantle_back" -> nextBack = now + 150;
            case "dismantle_down" -> nextDown = now + 480;
            default -> { }
        }
        entityData.set(ANIM, name);
        if (name.equals("possession")) level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void end(long cooldown) {
        long now = level().getGameTime();
        switch (skill) {
            case "cleave" -> nextCleave = now + 100;
            case "fuga" -> nextFuga = now + 400;
            case "furnace" -> nextFurnace = now + 600;
            case "shrine" -> nextShrine = now + 2400;
            default -> { }
        }
        if (skill.startsWith("attack")) {
            comboIndex++;
            nextAttack = now + 6;
        } else {
            nextAttack = now + 15;
        }
        skill = "";
        skillTick = 0;
        setNoGravity(false);
        entityData.set(ANIM, "");
    }

    private void faceTarget() {
        LivingEntity t = getTarget();
        if (t == null) return;
        double dx = t.getX() - getX();
        double dz = t.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Vec3 aim() {
        LivingEntity t = getTarget();
        if (t != null) {
            Vec3 d = t.getBoundingBox().getCenter().subtract(getEyePosition());
            if (d.lengthSqr() > 1.0E-4D) return d.normalize();
        }
        return getLookAngle();
    }

    private void runSkill(ServerLevel level) {
        skillTick++;
        getNavigation().stop();
        Vec3 m = getDeltaMovement();
        boolean airborne = skill.equals("dismantle_back") || skill.equals("dismantle_down");
        setDeltaMovement(0.0D, airborne ? m.y : Math.min(0.0D, m.y), 0.0D);
        if (!skill.equals("shrine") && !skill.equals("possession")) faceTarget();

        switch (skill) {
            case "possession" -> {
                if (skillTick % 10 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0D, getZ(), 6, 0.5D, 0.9D, 0.5D, 0.03D);
                }
                if (skillTick >= POSSESSION_TICKS) {
                    possessed = true;
                    end(0);
                }
            }
            case "dismantle" -> {
                // sukuna_ab1: move 11 -> 1, the slash is made at move 5 (tick 7), animation dismantle1
                if (skillTick == 7) {
                    Vec3 dir = aim();
                    DismantleSlashEntity.launch(level, this, DismantleSlashEntity.QUICK, getEyePosition().add(dir.scale(2.0D)), dir);
                    BedrockFxEntity.play(level, "quick_cast", getX(), getY(), getZ(), getYRot());
                }
                if (skillTick >= 11) end(0);
            }
            case "dissect" -> dissectTick(level);
            case "dismantle_back" -> backTick(level);
            case "dismantle_down" -> downTick(level);
            case "cleave" -> {
                if (skillTick == 4) cleave(level);
                if (skillTick >= 10) end(0);
            }
            case "fuga" -> {
                if (skillTick > 5 && skillTick < FUGA_CHARGE && skillTick % 3 == 0) {
                    level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.6D, getZ(), 6, 0.5D, 0.5D, 0.5D, 0.04D);
                }
                if (skillTick == FUGA_CHARGE + 6) fireArrows(level, 1, 0.0D);
                if (skillTick >= FUGA_TOTAL) end(0);
            }
            case "furnace" -> {
                if (skillTick > 20 && skillTick < 100 && skillTick % 4 == 0) {
                    level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 2.0D, getZ(), 4, 1.2D, 0.8D, 1.2D, 0.0D);
                    level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.5D, getZ(), 10, 1.5D, 1.0D, 1.5D, 0.05D);
                }
                if (skillTick == 100 || skillTick == 112 || skillTick == 124) fireArrows(level, 3, 0.18D);
                if (skillTick == 140) furnaceBlast(level);
                if (skillTick >= FURNACE_TOTAL) end(0);
            }
            case "shrine" -> {
                if (skillTick == 100) {
                    var shrine = ModEntities.MALEVOLENT_SHRINE.get().create(level);
                    if (shrine != null) {
                        shrine.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                        shrine.setOwner(this);
                        level.addFreshEntity(shrine);
                    }
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4.0F, 0.4F);
                    level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.0D, getZ(), 1, 0, 0, 0, 0);
                }
                if (skillTick >= SHRINE_TOTAL) end(0);
            }
            default -> comboHit(level);
        }
    }

    private void comboHit(ServerLevel level) {
        int idx = 0;
        for (int i = 0; i < COMBO.length; i++) if (COMBO[i].equals(skill)) idx = i;
        int total = COMBO_TICKS[idx];
        if (skillTick == (int) (total * 0.4F)) {
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (idx == 4 ? 1.8F : 1.0F);
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(3.6D), this::isFoe)) {
                Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 1.0E-4D && to.normalize().dot(look) < 0.2D) continue;
                e.hurt(damageSources().mobAttack(this), dmg);
                Vec3 push = to.lengthSqr() > 1.0E-4D ? to.normalize().scale(idx == 4 ? 1.2D : 0.5D) : Vec3.ZERO;
                e.push(push.x, 0.25D, push.z);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, 0.7F);
        }
        if (skillTick >= total) end(0);
    }

    /** Horizontal push along {@code dir} (unit), used for the addon's ds:knockback dashes; the strengths are estimates. */
    private void dash(Vec3 dir, double speed) {
        Vec3 h = new Vec3(dir.x, 0.0D, dir.z);
        if (h.lengthSqr() < 1.0E-6D) return;
        h = h.normalize().scale(speed);
        setDeltaMovement(h.x, getDeltaMovement().y, h.z);
    }

    private Vec3 flat() {
        Vec3 a = aim();
        Vec3 h = new Vec3(a.x, 0.0D, a.z);
        return h.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : h.normalize();
    }

    /**
     * sukuna_ab1d (解·切 / dissect), move 36 -> 1: flashstep at moves 33..35 and 25..27, back-step at move 34, dash forward
     * at moves 27..32, the slash is made at move 15 (tick 22), voice at move 23.
     */
    private void dissectTick(ServerLevel level) {
        Vec3 f = flat();
        if (skillTick == 3 || skillTick == 11) BedrockFxEntity.play(level, "flashstep", getX(), getY(), getZ(), getYRot());
        if (skillTick >= 3 && skillTick <= 5) dash(f.scale(-1.0D), 0.9D);
        if (skillTick >= 8 && skillTick <= 13) dash(f, 0.8D);
        if (skillTick == 14) BedrockFxEntity.play(level, "voice_kai", getX(), getY(), getZ(), getYRot());
        if (skillTick == 22) {
            Vec3 dir = aim();
            DismantleSlashEntity.launch(level, this, DismantleSlashEntity.DISSECT, position().add(0.0D, 1.0D, 0.0D).add(dir), dir);
            BedrockFxEntity.play(level, "dissect_cast", getX(), getY(), getZ(), getYRot());
        }
        if (skillTick >= 36) end(0);
    }

    /**
     * sukuna_ab1e (后撤宽斩 / wide dismantle), move 41 -> 1: leap back at move 39, drift back until move 34, lunge at
     * moves 29..30, come down at 22..25, ground slam and sounds at move 21, the wide slash is made at move 20 (tick 22).
     */
    private void backTick(ServerLevel level) {
        Vec3 f = flat();
        if (skillTick == 3) {
            BedrockFxEntity.play(level, "leap", getX(), getY(), getZ(), getYRot());
            setDeltaMovement(getDeltaMovement().x, 0.55D, getDeltaMovement().z);
        }
        if (skillTick >= 3 && skillTick <= 8) {
            Vec3 d = getDeltaMovement();
            dash(f.scale(-1.0D), 0.9D);
            setDeltaMovement(getDeltaMovement().x, d.y, getDeltaMovement().z);
        }
        if (skillTick == 12 || skillTick == 13) dash(f, 1.2D);
        if (skillTick >= 17 && skillTick <= 20 && getDeltaMovement().y > -0.8D) {
            setDeltaMovement(getDeltaMovement().x, -0.8D, getDeltaMovement().z);
        }
        if (skillTick == 21) BedrockFxEntity.play(level, "wide_cast", getX(), getY(), getZ(), getYRot());
        if (skillTick == 22) {
            Vec3 dir = aim();
            DismantleSlashEntity.launch(level, this, DismantleSlashEntity.WIDE, position().add(0.0D, 1.0D, 0.0D).add(dir), dir);
        }
        if (skillTick >= 41) end(0);
    }

    /**
     * sukuna_ab1b (空中解 / downwards), move 66 -> 1: rises and hovers, three slashes fall from his position at moves
     * 25, 23 and 21 (below him, 3 blocks in front, 3 blocks to the west); each makes a 10 block shockwave where it lands.
     */
    private void downTick(ServerLevel level) {
        if (skillTick == 1) BedrockFxEntity.play(level, "voice_done", getX(), getY(), getZ(), getYRot());
        if (skillTick == 3) setNoGravity(true);
        if (skillTick >= 3 && skillTick <= 14) setDeltaMovement(0.0D, 0.5D, 0.0D);
        else if (skillTick > 14 && skillTick < 47) setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (skillTick == 47) setNoGravity(false);
        if (skillTick == 42 || skillTick == 44 || skillTick == 46) {
            Vec3 o = skillTick == 42 ? new Vec3(0, -1, 0) : skillTick == 44 ? new Vec3(0, -1, 3) : new Vec3(-3, -1, 0);
            Vec3 p = position().add(o);
            DismantleSlashEntity.launch(level, this, DismantleSlashEntity.DOWN, p, new Vec3(0, -1, 0));
        }
        if (skillTick >= 66) end(0);
    }

    @Override
    public boolean isDismantleTarget(Entity target) {
        return isFoe(target);
    }

    private void cleave(ServerLevel level) {
        LivingEntity t = getTarget();
        if (t == null || distanceToSqr(t) > 5.0D * 5.0D) return;
        // Cleave scales with the target's toughness.
        float dmg = Math.min(60.0F, Math.max(10.0F, t.getMaxHealth() * 0.15F));
        t.hurt(damageSources().indirectMagic(this, this), dmg);
        level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6D, t.getZ(), 20, 0.4D, 0.5D, 0.4D, 0.2D);
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    private void fireArrows(ServerLevel level, int count, double spread) {
        Vec3 base = aim();
        for (int i = 0; i < count; i++) {
            SukunaFlameArrowEntity arrow = ModEntities.SUKUNA_FLAME_ARROW.get().create(level);
            if (arrow == null) return;
            double off = count == 1 ? 0.0D : (i - (count - 1) / 2.0D) * spread;
            Vec3 dir = base.add(-base.z * off, 0.0D, base.x * off).normalize();
            Vec3 origin = getEyePosition().add(dir.scale(1.5D));
            arrow.setPos(origin.x, origin.y, origin.z);
            arrow.launch(this, null, dir);
            level.addFreshEntity(arrow);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void furnaceBlast(ServerLevel level) {
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(9.0D),
                x -> isFoe(x) && x.distanceToSqr(this) <= 81.0D)) {
            e.hurt(damageSources().indirectMagic(this, this), 20.0F);
            e.setSecondsOnFire(6);
        }
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.0D, getZ(), 150, 4.0D, 1.5D, 4.0D, 0.15D);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    // ------------------------------------------------------------------ domain clash

    @Override public ClashKind clashKind() { return ClashKind.MALEVOLENT_SHRINE; }

    /** The cast animation is the casting phase (1..100); the shrine then stands until the skill ends. */
    @Override
    public int clashCastTicks() {
        if (!skill.equals("shrine")) return -1;
        return skillTick <= 100 ? skillTick : 1000;
    }

    @Override
    public void clashBegin() {
        if (!(level() instanceof ServerLevel server)) return;
        if (skill.equals("shrine")) end(0);
        nextShrine = Math.max(nextShrine, server.getGameTime() + 2400);
        for (DomainShrineEntity shrine : server.getEntitiesOfClass(DomainShrineEntity.class, getBoundingBox().inflate(64.0D),
                s -> getUUID().equals(s.getOwnerUuid()))) {
            shrine.discard();
        }
    }

    @Override public void clashHold(boolean hold) { this.clashHold = hold; }

    /** The Malevolent Shrine won: it is cast again where Sukuna stands. */
    @Override
    public void clashWin(DomainClashEntity clash) {
        if (!skill.isEmpty()) end(0);
        start("shrine");
    }

    @Override public void clashLose() { }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (skill.equals("possession")) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public java.util.List<String> castableSkills() {
        return java.util.List.of("attack1", "attack2", "attack3", "attack4", "attack5",
                "dismantle", "dissect", "dismantle_back", "dismantle_down", "cleave", "fuga", "furnace", "shrine", "possession");
    }

    @Override
    public boolean cast(String name, LivingEntity target) {
        if (!skill.isEmpty() || !castableSkills().contains(name)) return false;
        if (!name.equals("possession")) possessed = true;
        start(name);
        return true;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Possessed", possessed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        possessed = tag.getBoolean("Possessed");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            String a = entityData.get(ANIM);
            if (!a.isEmpty()) return state.setAndContinue(anim(a));
            return state.setAndContinue(anim(state.isMoving() ? "walk" : "idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
