package com.jujutsuenhancement.quickmenu;

import com.jujutsuenhancement.combat.GojoSkills;
import com.jujutsuenhancement.combat.KatanaCombat;
import com.jujutsuenhancement.combat.SukunaCleave;
import com.jujutsuenhancement.combat.SukunaDomain;
import com.jujutsuenhancement.combat.SukunaFists;
import com.jujutsuenhancement.combat.SukunaFour;
import com.jujutsuenhancement.combat.SukunaFuga;
import com.jujutsuenhancement.combat.SukunaMax;
import com.jujutsuenhancement.combat.SukunaSkills;
import com.jujutsuenhancement.combat.SukunaTechnique;
import com.jujutsuenhancement.combat.YujiSkills;
import com.jujutsuenhancement.registry.ModItems;
import com.jujutsuenhancement.stats.Energy;
import com.jujutsuenhancement.stats.PlayerStats;
import com.jujutsuenhancement.text.BedrockText;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The functions the quick-command buttons run ({@code functions/cheats/*.mcfunction} and {@code functions/cooldowns.mcfunction}). */
public final class QuickCommands {
    private QuickCommands() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static void run(ServerPlayer p, int category, int index) {
        if (category < 0 || category >= QuickMenu.CATEGORIES.size()) return;
        if (index < 0 || index >= QuickMenu.CATEGORIES.get(category).buttons().size()) return;
        try {
            switch (category) {
                case 0 -> player(p, index);
                case 1 -> abilities(p, index);
                case 2 -> buildings(p, index);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            p.sendSystemMessage(BedrockText.parse("§c快捷指令执行失败：" + QuickMenu.CATEGORIES.get(category).buttons().get(index)));
        }
    }

    private static void cheer(ServerPlayer p) {
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        com.jujutsuenhancement.net.ModNet.shake(p, 4.0D, 5);
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(BedrockText.parse(text));
    }

    // ------------------------------------------------------------------ 玩家强化与多项升级

    private static void player(ServerPlayer p, int i) {
        switch (i) {
            case 0 -> { // cheats/maxout
                say(p, "§l§e已满级");
                boolean pct120 = p.getTags().contains("120percent");
                PlayerStats.set(p, "grade", 5);
                PlayerStats.set(p, "level", 250);
                PlayerStats.set(p, "agility", pct120 ? 60 : 50);
                PlayerStats.set(p, "endurance", pct120 ? 120 : 100);
                PlayerStats.set(p, "strength", pct120 ? 60 : 50);
                PlayerStats.set(p, "damageinsurance", pct120 ? 12 : 10);
                PlayerStats.set(p, "damageadd", pct120 ? 12 : 10);
                PlayerStats.set(p, "energystat", 50);
                int base = PlayerStats.get(p, "start") == 0 ? 100 : PlayerStats.get(p, "EnergyCap");
                Energy.setBase(p, base + 10000);
                Energy.set(p, 999999);
            }
            case 1 -> { // cheats/maxoutsukuna
                if (p.getTags().contains("yuji_awakened")) {
                    say(p, "§l§4宿傩不想寄生在这具躯体上\n\n ( 移除你的术式 )");
                    return;
                }
                p.addTag("sukuna");
                p.addTag("has_sukuna");
                int base = PlayerStats.get(p, "start") == 0 ? 100 : PlayerStats.get(p, "EnergyCap");
                Energy.setBase(p, base + 200000);
                SukunaTechnique.setFingers(p, 20);
                SukunaTechnique.setActive(p, true);
                ItemStack st = new ItemStack(ModItems.SUKUNA_TECHNIQUE.get());
                if (!p.getInventory().contains(st)) p.getInventory().add(st);
                SukunaTechnique.grant(p);
                say(p, "§l§4宿傩 §l§c接管了身体");
            }
            case 2 -> { // cheats/set_max_fever
                PlayerStats.set(p, "fever", 100);
                cheer(p);
            }
            case 3 -> { // cheats/set_max_fuga
                SukunaTechnique.setFuga(p, 600);
                SukunaTechnique.addFuga(p, 0);
                cheer(p);
            }
            case 4 -> { // cooldowns
                GojoSkills.resetCooldowns(p);
                YujiSkills.resetCooldowns(p);
                SukunaSkills.resetCooldowns(p);
                SukunaFists.resetCooldowns(p);
                SukunaCleave.resetCooldowns(p);
                SukunaFour.resetCooldowns(p);
                SukunaFuga.resetCooldowns(p);
                SukunaMax.resetCooldowns(p);
                SukunaDomain.resetCooldowns(p);
                KatanaCombat.resetCooldowns(p);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 能力与特质获取

    private static void abilities(ServerPlayer p, int i) {
        switch (i) {
            case 0 -> { // give_heavenlyrestriction
                p.addTag("heavenlyrestriction_zenin");
                p.addTag("heavenlyrestriction");
                cheer(p);
            }
            case 1 -> {
                PlayerStats.set(p, "divergentfistown", 1);
                PlayerStats.set(p, "divergentfistsown", 1);
                cheer(p);
                say(p, "§l§f已被给予技能 §l§t咒力缠绕");
            }
            case 2 -> {
                PlayerStats.set(p, "domainampown", 1);
                cheer(p);
                say(p, "§l§f已被给予技能 §l§b领域展延");
            }
            case 3 -> { // giverct
                p.addTag("reversecursedobtain");
                p.addTag("reversecursed");
                cheer(p);
            }
            case 4 -> {
                PlayerStats.set(p, "simpledomainown", 1);
                cheer(p);
                say(p, "§l§f已被给予技能 §l§b简易领域");
            }
            case 5 -> {
                p.addTag("sixeyes");
                cheer(p);
                say(p, "§l§f已被给予特质 §l§b六眼-苍天之瞳");
            }
            case 6 -> { // learn_world_slash: sukuna5 with 18 fingers
                if (SukunaTechnique.fingers(p) >= 18) {
                    ItemStack st = new ItemStack(ModItems.SUKUNA5.get());
                    if (!p.getInventory().contains(st)) p.getInventory().add(st);
                }
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
                com.jujutsuenhancement.net.ModNet.shake(p, 4.0D, 5);
                say(p, "§l§f---[ 新技能解锁 ]---\n§l§o§d空间斩 §r§l§e扩张了术式对象到空间，存在，甚至于世界本身的斩击");
            }
            case 7 -> {
                PlayerStats.set(p, "hollowbasketown", 1);
                PlayerStats.set(p, "hollowbasketsown", 1);
                cheer(p);
                say(p, "§l§f---[ 新技能解锁 ]---\n§l§e弥虚葛笼 可在身周创造出像笼子一般的球状领域，是 新·阴流: 简易领域 的原型");
            }
            case 8 -> { // tame_all_shikigami (the Ten Shadows technique is not ported yet: only the flags are kept)
                for (String s : new String[]{"rabbit", "toad", "dog", "nue", "serpent", "ox", "elephant", "deer"}) PlayerStats.set(p, "tame_" + s, 1);
                say(p, "§l§a已调伏除魔虚罗外所有式神，如果您想调伏魔虚罗请自行进行调伏仪式");
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 建筑物生成

    private static void fill(ServerLevel level, BlockPos origin, int dx1, int dy1, int dz1, int dx2, int dy2, int dz2, Block block) {
        int x1 = Math.min(dx1, dx2), x2 = Math.max(dx1, dx2), y1 = Math.min(dy1, dy2), y2 = Math.max(dy1, dy2), z1 = Math.min(dz1, dz2), z2 = Math.max(dz1, dz2);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) level.setBlock(m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z), block.defaultBlockState(), 2);
    }

    private static void buildings(ServerPlayer p, int i) {
        ServerLevel level = p.serverLevel();
        BlockPos o = p.blockPosition();
        switch (i) {
            case 0, 1 -> { // bedrock_arena / stone_arena: 61 x 61 floor, 16 blocks of air above it
                fill(level, o, 30, 3, 30, -30, 0, -30, Blocks.AIR);
                fill(level, o, 30, 7, 30, -30, 4, -30, Blocks.AIR);
                fill(level, o, 30, 8, 30, -30, 11, -30, Blocks.AIR);
                fill(level, o, 30, 12, 30, -30, 15, -30, Blocks.AIR);
                if (i == 0) {
                    fill(level, o, 29, -3, 29, -29, -1, -29, Blocks.OBSIDIAN);   // as the addon: the obsidian is overwritten by the bedrock
                    fill(level, o, 30, -3, 30, -30, -1, -30, Blocks.BEDROCK);
                } else {
                    fill(level, o, 30, -3, 30, -30, -1, -30, Blocks.SMOOTH_STONE);
                    fill(level, o, 29, -3, 29, -29, -1, -29, Blocks.STONE);
                }
                level.playSound(null, o, SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            case 2 -> say(p, "§e咒术高专建筑 的结构文件尚未移植（将在建筑物阶段做）");
            default -> {
            }
        }
    }
}
