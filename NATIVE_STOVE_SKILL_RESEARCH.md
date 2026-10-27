# 原版宿傩火炉技能：静态调用面审计

## 范围
针对随项目提供的 JujutsuCraft v50.1 Forge 1.20.1 JAR，使用 `javap -c -p` 检查原版过程方法；这是字节码静态检查，不是游戏运行验证。

## 已核实
- `AISukunaProcedure.execute(LevelAccessor, double, double, double, Entity)` 存在；字节码读取实体 `skill` 状态，并检查原版 `COOLDOWN_TIME`、`COOLDOWN_TIME_COMBAT` 等效果。它是原版 AI 调度过程，不是单独的“火炉”函数。
- `AIFlameArrowProcedure.execute(LevelAccessor, double, double, double, Entity)` 存在；字节码涉及原版 `FlameArrowEntity`，并读写/检查 `cnt1`、`skill` 等实体状态。
- `SelectSukunaProcedure.execute(...)` 会处理 `jujutsucraft:skill_sukuna` 玩家进度，并继续调用 `SelectedProcedure`、装备流程。它属于玩家选择/初始化路径，不能当作 NPC 技能开关直接调用。
- `CursedTechniqueSukunaProcedure.execute(...)` 会分派到 `SkillSukunaCombo1/2/3Procedure`。

## 工程结论
这些签名和状态引用支持继续追踪原版火炉路径，但尚不足以证明直接调用 `AIFlameArrowProcedure` 就会完整启动火炉。仍需确认哪个调度分支设置对应的 `skill` 编号、`cnt1` 时序、目标/朝向、动画与冷却，以及 NPC 所需的初始化状态。自定义 `HeianSukunaEntity` 目前不是原版 `SukunaEntity` / `SukunaFushiguroEntity` 类型；直接调用前必须审查 `instanceof` 分支和所有副作用。当前没有将原版过程接入运行时，避免引入未经验证的崩溃或空技能状态。

## 可复现检查
运行 `python3 tools/audit_native_stove_skill.py`。检查通过只代表静态签名/字节码断言通过，不代表编译成功或游戏内技能已工作。
