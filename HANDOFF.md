# 交接说明：把《咒术觉醒》(Bedrock) 的角色移植成 Forge 1.20.1 独立生物

这份说明写给接手的人（或 AI）。先读完这一页再动代码。

## 1. 目标（用户原话要点）

用户（中文，玩 Minecraft Java 1.20.1 Forge，同时装了 JujutsuCraft ver51）想要：

- 把基岩版附加包「咒术觉醒」里的角色、技能、动画、AI、领域对抗，做进 Forge 版。
- **原模组 JujutsuCraft 一点都不改**，这个模组只新增自己的生物。
- 要求「和基岩版一模一样」：技能、连招、效果、AI、领域对抗都要对得上，不能只换动画。
- 皮肤不改，只有真人的「遍杀即灵体」形态用基岩版皮肤。

角色与内容清单：

| 角色 | 要有的内容 |
|---|---|
| 真人 | 全部技能、0.2 秒领域「自闭圆顿裹」、遍杀即灵体形态、改造人 / 几魂异型召唤 |
| 宿傩 | 两种火焰灶 / 开、伏魔御厨子、附身伏黑惠动画 |
| 五条悟 | 三种虚式茈（普通；蓄力 5 秒 200% 且见到宿傩附身的惠时自动放一次；蹲下无限制，残血自动放）、无量空处、0.2 秒无量空处 |
| 虎杖 / 日车 | 体术 + 领域；处刑人之剑技能 |
| 通用 | 领域对抗、黑闪、特殊处决（虎杖×真人、五条悟 NPC、宿傩 NPC、完全体宿傩×真希 NPC）、真希 NPC、魔虚罗召唤 |
| 彩蛋 | 高专五条悟×伏黑甚尔、虎杖×真人 |
| 觉醒系 | 觉醒虎杖 / 狗卷 / 鹿紫云 / 「一」/ 秤金次 的技能 |

## 2. 工程

- Forge 47.4.26 / Minecraft 1.20.1 / 官方映射 / JDK 17 / GeckoLib 4.8.4。
- 构建：`./gradlew build`（需要能联网拉 Forge 和 GeckoLib）。产物 `build/libs/jujutsuenhancement-0.1.0.jar`。
- 我这边的循环：改代码 → `git push origin main` → GitHub Actions（`.github/workflows/build.yml`）构建 → 结果推到分支 `ci-output`（`build.log`、`errors.txt`、`outcome.txt`、jar）。仓库是用户自己的：`github.com/cwb123456789/jjk-port`。
- 模组 id `jujutsuenhancement`，包名 `com.jujutsuenhancement`。
- 和 JujutsuCraft 的唯一联系：按资源 id 引用它的贴图（如 `jujutsucraft:textures/entities/gojo_satoru.png`）、物品（`jujutsucraft:executioners_sword`、`jujutsucraft:gavel`）、音效（`jujutsucraft:slow_motion_end`）。没有对它的类依赖。

## 3. 代码地图（`src/main/java/com/jujutsuenhancement`）

| 文件 | 内容 |
|---|---|
| `entity/GojoAwakenedEntity` | 五条悟：5 连击、三种茈、两种领域（领域 = 范围内定身，持续 900 tick）；同步字段 `DOMAIN_FX` 驱动客户端画面 |
| `entity/HollowPurpleEntity` | 茈弹体。按基岩版脚本：每 tick 移动 4 格（200% 版前 12 tick 慢、之后 5 格），每 tick 范围伤害加删方块，仅在 mobGriefing 开启时破坏地形 |
| `entity/MahitoAwakenedEntity` | 真人：三种形态、遍杀即灵体（HP≤50%）、领域 |
| `entity/SukunaAwakenedEntity` | 宿傩：5 连击、解、捌、开（火焰箭）、灶、伏魔御厨子、附身过场 |
| `entity/YujiAwakenedEntity` | 虎杖：连招、黑闪（连击越多概率越高）、对遍杀即灵体真人的处决 |
| `entity/HigurumaAwakenedEntity` | 日车：法槌、审判领域（有罪判决）、处刑人之剑 |
| `entity/TestCastable` | 测试用：右键让生物放下一个技能，潜行右键切换 |
| `integration/AwakenedCommands` | `/jje skills`、`/jje fight`、`/jje cast <技能名>` |
| `client/DomainVoidFx` | 无量空处的全屏画面（白光、光线隧道、墨迹天空、金环、星云），是 `IGuiOverlay` |
| `client/*Renderer`、`client/model/*` | GeckoLib 渲染和模型 |
| `registry/*` | 实体、刷怪蛋、属性注册 |
| `integration/Native*Bridge`、`MahitoPrototype*` | **已停用**（以前尝试改原模组行为的桥接，用户后来要求原模组不动） |

资源在 `src/main/resources/assets/jujutsuenhancement/`：`animations/`、`geo/`、`textures/`、`lang/`。
动画和模型由 `tools/build_*_assets.py` 从基岩版资源包生成（用法：`python3 tools/build_gojo_assets.py <基岩资源包目录> src/main/resources/assets/jujutsuenhancement`）。

## 4. 基岩版附加包怎么读

- 技能逻辑在行为包 `functions/**.mcfunction`，用 scoreboard 计数器和命令写成。**计数器是往下减的**（`move`、`Deleter`、`domain`、`atk`、`Stun`、`Frames` 等），经过的 tick 数 = 起始值 − 当前值。
- `scripts/index.js` 里 `ds:shootprojectile ... velocity:N` 表示弹体每 tick 沿视线方向飞 N 格（最多 200 tick）。
- 需求 → 附加包文件的完整对照在 `docs/移植对照表.md`。五条悟相关：`limitless/hollow_purple`、`200hollow_purple`、`imaginary_purple`、`unlimited_void`、`gojo_domain`、`gojo_domain_quick`、`mobs/sorcerers/student_gojo/*`；领域对抗：`domain_clash_main`、`domain_clash_starting`、`domain_clasher_failed`、`dev_endclash`、`m1_clash`、`hr/deadly_clash`、`scripts/domain_clashing.js`。
- `tools/mcsum.py` 把长长的 mcfunction 压成可读摘要（去掉选择器里一长串排除项）。
- **接手者需要用户另外提供**：基岩版附加包（行为包 + 资源包）本身，以及 JujutsuCraft ver51 的 jar。这个压缩包里没有，因为它们不是我写的，也不该由我转发。

## 5. 实话：哪些是还原的，哪些是我的近似

**照着 mcfunction / 脚本读出来、数值对得上的：**
- 五条悟三种茈的飞行速度、伤害范围、破坏范围、寿命。
- 无量空处的锁定机制：基岩版里它没有直接伤害，范围内所有人被定身（Stun / Move / Disabled 每 tick 刷新）直到领域结束。
- 各角色动画（从基岩版动画文件转出，骨骼名对过）。

**我的近似（用户已经指出和基岩版不一样的地方就在这里）：**
- 角色 AI（什么时候放什么技能）基本是我按描述写的，**没有逐个对照** `mobs/sorcerers/*` 下的触发逻辑。
- 五条悟 5 连击的伤害数值、真人 / 宿傩 / 虎杖 / 日车的大部分技能的伤害和范围，是估的。
- 粒子、冲击帧过场、音效基本没做。
- 领域画面（`DomainVoidFx`）是用户发的一段**原模组**无量空处录像逐帧拆出来后我自己画的，不是基岩版的样子，也**没在游戏里实际跑过**（只确认能编译、jar 完整）。

**完全没做：**
领域对抗、黑闪处决（五条悟 / 宿傩 NPC、完全体宿傩×真希）、真希 NPC、魔虚罗召唤、两个彩蛋、觉醒狗卷 / 鹿紫云 / 「一」/ 秤金次、真人的改造人 / 几魂异型召唤、虎杖领域、日车审判者模型、玩家自己可用的技能（现在都是生物放技能）。

「一」在附加包里没找到对应文件名，需要向用户确认指的是谁。

## 6. 踩过的坑

- 原模组自己的客户端有按键空指针（FCL 启动器上），不是这个模组的问题。
- 茈弹体必须注册渲染器（用 `NoopRenderer`），否则它一进视野客户端就崩。
- 用户手机上下载 jar 会被截断（`zip END header not found`）。发 jar 时一定写明字节数让用户核对，并让他删掉旧 jar。
- 生物只会自动攻击怪物和非创造模式玩家。测试时用右键或 `/jje cast`，指定目标后创造模式玩家也能被打。
- 区域内的锁定靠药水效果加 `setNoAi(true)`，结束时必须还原，所以 `remove()` 里有清理。

## 7. 测试

- `/jje skills`：列出附近觉醒生物能放的技能。
- `/jje cast <技能>`：让最近的觉醒生物（48 格内）放该技能，如 `domain`、`quick_domain`、`purple200`、`unlimited`。
- 右键生物放下一个技能，潜行右键换下一个。
- 刷怪蛋在创造模式物品栏的「Spawn Eggs」页。

## 8. 给接手者的硬规则（r72 审查后加）

1. **弹体穿过方块**，不要加方块碰撞。基岩版脚本没有方块判断，地形由函数每 tick 挖。
2. 五条悟普通茈播的是 `animation.hollowpurple`（24 秒，`purple_normal`），不是 `limitless_purple`（`purple_cutscene`）。
3. `gojo_awakened.animation.json` 保持 Bedrock 写法（`post` + `lerp_mode: catmullrom`），不要转成 `vector` + `linear`。
4. `tools/audit_*.py` 是文字匹配，不能证明和基岩版一致。行为只依据 mcfunction 和脚本；检查和源码冲突就改检查。
5. 改动请写清楚「对照了基岩版哪个文件」。详见 `docs/REVIEW_r72.md`、`docs/REVIEW_2026-11-06.md`。
6. **原模组不动。** `NativePurpleBridge`、`NativeDomainClashBridge`、`NativeMahitoFormBridge` 保持停用（不要加回 `@Mod.EventBusSubscriber`），因为用户要求只新增自己的生物，不改原模组的技能。
7. 这个工程目前只能靠 GitHub Actions 编译（本地通常联不上 Gradle / Forge 仓库，也可能只有 Java 21）。没编译、没进游戏测过的改动，要在说明里写明。

## 9. 目录说明

- `src/`、`tools/`、`docs/`、`build.gradle` 等：当前工程。
- `old-notes/`：早期几十份检查记录（那时还在尝试改原模组），**大多已过时**，只在想查某个历史决定时翻。
- 压缩包里没有 `.git` 历史，也没有任何 jar。

## Impact frames (2026-11-10, v16)
- The newest `.mcaddon` is the "impact frames on" build of the behavior pack: it only differs from the previous one by
  not adding the `impactframesoff` tag in `add_scores.mcfunction`. Every `title @a[tag=!impactframesoff,...] title \ueXYZ`
  is a full-screen picture: cell XYZ of the glyph page `glyph_E3` (16x16 cells, 172 px each, frames ~172x104).
- `tools/build_glyph_frames.py <glyph_E3 page> <assets dir> [low bytes in hex]` cuts the frames into
  `textures/bedrock/glyph/e3xx.png` (+ `.mcmeta` with blur). Only the frames in use are committed (E330/32/34/35/36/38/39/3C);
  the blank 1-px glyphs (E333, E337, E33B) are intentionally absent = "empty title".
- `client/bedrock/ImpactFrames` is the title state (show = `title <glyph>` with the 20-tick stay from `times 0 20 0`,
  clear = `title §l`) and a gui overlay drawn over the fades. `VoidScript.impactFrame(e)` (e = 761 - domain) and
  `VoidScript.impactFrameBreak(k)` (k = 25 - Deleter) feed it.
- Not yet wired (glyph use found with a grep over the pack): E33C domain-end flash in every other domain
  (malevolent_shrine, shadow_garden, storm_domain, yuji domain, deadly_sentencing...), E33D letterbox bars (clashes, cutscenes),
  E35F generic hit flash, E3A0-E3BC black-flash finisher (blackflash_finisher_gojo), E350-E356 / E3CF-E3E8 (forced_roles, skyfall),
  E370-E37C (idledeathgamble2), E390-E398 (lightning_am2), E3C1-E3C6 (hr4), E3E9-E3EE (bluecore). Glyph pages F3xx are NOT supplied yet.
- Unverified: whether Bedrock draws the title above or below `camera fade`; we draw it above.

## Domain clash (2026-11-12, v17) — compiled only, never run in game
Source of truth: Bedrock `domain_clash_starting`, `domain_clash_main`, `domain_clasher_failed`, `scripts/domain_clashing.js`.
- Server: `entity/DomainClashEntity` (invisible entity, synched T/phase/slots). `DomainClashEntity.check(caster)` is called from each
  caster's serverTick (Gojo/Sukuna/Mahito/Higuruma implement `ClashCaster`). Trigger = caster domain timer 4..70 and another
  caster's domain within 48 blocks. Timeline: t=3 shockwave, 40 fade, 60 barrier platform (±33, y300/299), 61 teleport
  (slot0 z+33, slot1 z-33, slot2 x+33, slot3 x-33), 65 domain visuals, ~208 resistances, 210 fight (<=595 ticks; damage d lowers
  resistance by floor(d); Shrine drains 1/tick from others; res 0 or death = fail), resolve (white fade, 1 s stun), winner =
  highest resistance, winner's domain continues (Unlimited Void keeps the sky arena), then everyone is sent home.
- Resistances: UV 700 (the video showed 750 once — kept 700 from the function), Shrine 250, Perfection 600, Deadly Sentencing 600.
  Tempest/Idle/Shadow/Speech/Yuji/Iron/Incomplete kinds are NOT wired (no caster class for them yet).
- Client: `client/bedrock/ClashShow` (shockwave, shake, fades, camera shots, per-domain particle lists from
  `bedrock/clash/effects.json`, smash on a broken domain). `VoidShow` has a clash-win mode (`clashMode`) for Gojo's UV after a win.
- `tools/build_clash_assets.py` exports the ~70 clash particle JSONs/textures/sounds from the Bedrock pack.
- Not done / guessed: clash music (files not supplied), Sukuna-wins long cinematic (3000–3200 in Bedrock, simplified), camera
  keys and FOV were read from the function but not compared frame by frame with a recording, shake amplitude is estimated.
- Skill inventory of the 31 reference videos (Gojo, Higuruma, Sukuna) is in `docs/skill_videos.md`; it is a to-do list for
  auditing each character against its Bedrock functions, nothing there is ported yet.
