# JujutsuCraft 50.1 × 咒术觉醒 1.81 移植需求与实施顺序

## 目标与边界
- 目标运行环境：Minecraft Java 1.20.1、Forge 47.4.26、JujutsuCraft ver50.1。
- 基岩版咒术觉醒 v1.81 是素材与行为参考来源，不可将 Bedrock 的 entity/animation-controller/particle JSON 或 Script API 直接放入 Forge 并期待运行；必须转换为 Java/GeckoLib 资源与 Forge 逻辑。
- JujutsuCraft 本体技能逻辑、实体 AI 与键位尽量保留；优先替换视觉、模型、动画与特定技能触发逻辑。避免未经验证地覆盖整个原模组。
- “真人”按 Mahito（真人）理解；“遍杀即灵体”按用户指定的目标外观/角色名保留，需在实体映射中进一步确认具体 Bedrock 资源对应项。

## 用户要求的功能清单

### A. 真人 / Mahito
- 将 JujutsuCraft 真人相关技能的视觉效果、动作和领域表现替换为咒术觉醒版本。
- 真人普通皮肤不改为其他角色；另新增/替换为“遍杀即灵体”形态的皮肤和对应动画。
- 参考资源：`animations/mahito.animation.json`、`animations/mahito_effects.animation.json`，以及真人实体模型/纹理/黑闪处决粒子。

### B. 两面宿傩
- 灶 / Divine Flames（用户称“灶开”）：技能表现与动画。
- 伏魔御厨子：领域模型、开领域动作、持续特效。
- 附身伏黑惠形态：皮肤、动作、技能表现。
- 完全体宿傩：动作与对禅院真希 NPC 的黑闪处决动作。
- 参考资源：`animations/sukuna/divine_flames.animation.json`、`domain_expansions.animation.json`、`malevolent_shrine.animation.json`、`sukuna_awakenings.animation.json`、`sukuna_moves.animation.json`、`sukuna_finger.animation.json`。

### C. 五条悟
- 普通虚式茈：包含起手/过场/发射的完整动作。
- 200% 虚式茈：蓄力 5 秒后发射。
- 无限制虚式茈：蹲下触发；五条悟 NPC 生命值进入残血阈值时自动触发。
- NPC 自动反应：五条悟 NPC 看到伏黑惠宿傩形态后自动释放一次 200% 虚式茈，发射后对同一目标不重复释放（需要持久化/实体状态标记，定义重置条件）。
- 无量空处：领域展开动画与领域效果。
- 0.2 秒无量空处：蹲下触发的短时领域版本。
- 参考资源：`animations/gojo_high.animation.json`，200% 紫色相关粒子位于 `particles/limitless/200purple/`，无限/无量空处粒子位于 `particles/limitless/unlimited_void/`。

### D. 虎杖悠仁 / 日车宽见 / 领域对抗
- 虎杖吃宿傩手指的变身过场与形态切换。
- 日车领域展开与审判相关视觉效果。
- 领域对抗：两边领域起手、碰撞、胜负与结束过程。
- 参考资源：`animations/sukuna_finger.animation.json`、`animations/enraged_yuji_cutscene.animation.json`、`animations/domain_stuff/clashing_animations.animation.json`、`animations/judgeman.animation.json`、`animations/judgeman2.animation.json`。

### E. 黑闪与处决动作
- 替换通用黑闪特效。
- 虎杖对真人的特殊黑闪处决动作。
- 五条悟 NPC 黑闪处决动作。
- 完全体宿傩 NPC 对禅院真希 NPC 的黑闪处决动作。
- 需区分动画触发、目标校验、伤害结算、动画锁定、结束后恢复 AI；不能仅替换粒子。
- 参考资源：`particles/black_flash*.particle.json`、真人与虎杖动画、JujutsuCraft 的 `EntityBlackFlashEntity`、`AIBlackFlashProcedure` 与 `AttackTodoBlackFlashProcedure`。

### F. 魔虚罗
- 魔虚罗召唤仪式动画、出现过程、轮盘/模型与后续战斗动画替换为咒术觉醒资源。
- 参考资源：`animations/shikigami/mahoraga_ritual1.animation.json`、`mahoraga_player_interaction.animation.json` 与 `models/entity/shikigami/`。

## 实施顺序（按可验证的小步交付）
1. **资源映射与适配清单**：完成。本项目 `docs/BEDROCK_ANIMATION_SOURCE_INDEX.json` 和 `docs/bedrock-source-reference/` 保存所需源动画/模型参考副本。
2. **先打通单个静态领域渲染**：修复/验证 GeckoLib 模型与纹理加载，成功编译并在 Java 客户端召唤测试实体；未成功前不扩展到更多技能。
3. **转成 GeckoLib 动画**：优先伏魔御厨子开领域/持续动画，其次宿傩灶、五条虚式茈与无量空处，再做真人、虎杖、日车、魔虚罗。
4. **技能触发与 AI**：在 JujutsuCraft 的既有实体/技能逻辑基础上加可控钩子；实现五条一次性 200% 虚式茈、残血无限制虚式茈、蹲下技能、吃手指变身和领域对抗状态机。
5. **黑闪处决**：最后做角色/目标配对、动画锁定与伤害结算。
6. **回归测试**：无 JujutsuCraft / 有 JujutsuCraft、单人/服务器、NPC 目标重复检测、死亡/卸载/重进后状态。

## 关键技术风险
- Bedrock 的 Molang、动画控制器、事件、粒子和 Script API 与 Forge/GeckoLib 不兼容，动画 JSON 需要语义转换而不是简单改扩展名。
- 大型模型可能需要修正 UV、骨骼枢轴、缩放和坐标系。
- NPC 一次性技能需要明确“同一目标”定义以及何时重置标记；本阶段暂以每个五条实体实例对每个目标最多触发一次为默认设计，后续可调整。
- 本工程当前尚未成功构建，所有模型/动画/技能集成都必须以 Gradle 构建和客户端实测为准。

## 补充原则：以 JujutsuCraft 为底座，缺失技能新增
- 本项目不是独立重做模组，也不是用新模组取代 JujutsuCraft。目标是运行在 **JujutsuCraft ver50.1 + Forge 1.20.1** 的基础上，优先复用原模组已有实体、技能、伤害、冷却与战斗状态。
- **原模组已有对应技能：** 保留原技能的核心机制与兼容性，按需求替换其模型、皮肤、动作、动画、粒子、音效、领域表现；只有用户明确要求改变触发条件或技能机制时，才修改相应逻辑。
- **原模组没有的技能或动作：** 从基岩版《咒术觉醒》移植资源并新增 Forge 技能/实体逻辑，不因原模组缺少对应注册项而跳过。例如：蹲下触发的 0.2 秒无量空处、特定 NPC 条件触发的一次性 200% 虚式茈、指定角色配对的黑闪处决、吃手指变身过场等。新增内容必须与原模组的实体和战斗系统协同，不重复注册或破坏已有技能。
- **移植决策流程：** 先检查 JujutsuCraft 50.1 是否已有对应技能/实体/动画钩子；若有，替换或扩展现有实现；若没有，再添加新实现。每项功能记录为“替换 / 扩展 / 新增”，并记录对应源资源、代码入口和验证结果。
- **验收标准：** 不以资源已复制或 JSON 已生成作为完成标准。每项技能至少要通过工程构建；可运行后还需检查触发条件、动画播放、命中/伤害、冷却/重复触发以及与 JujutsuCraft 原技能的兼容性。
