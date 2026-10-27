# JujutsuCraft 原版伏魔御厨子过程：签名与调用前提

针对提供的 JujutsuCraft v50.1 Forge 1.20.1 JAR，使用 `javap -p` 和 `javap -c -p` 检查字节码。

## 已核实的方法签名

- `MalevolentShrineActiveProcedure.execute(LevelAccessor, Entity)`
- `MalevolentShrineProcedure.execute(LevelAccessor, double, double, double, Entity)`

## 字节码证据与风险

- `MalevolentShrineActiveProcedure` 读取实体 NBT 的 `skill`、`COOLDOWN_TICKS` 等字段，并查询原版领域扩张效果与地图变量 `DomainExpansionRadius`。因此不能只凭方法存在就断定传入自定义实体可正常发动技能。
- `MalevolentShrineProcedure` 会写入实体 NBT 的 `select`，调用 `DomainExpansionCreateBarrierProcedure.execute(...)`，并依赖 `cnt1` 等实体状态进入后续逻辑。仅直接调用该方法可能只启动过程的一部分，不能直接视为完整领域技能。
- 当前增强模组仍使用自定义 `DomainShrineEntity` 作为可玩原型。此研究步骤没有更改运行时技能调用，避免在尚未建立完整原版状态前触发不完整领域过程。

## 下一步接入条件

在尝试运行时调用前，需要确认：

1. 原版输入/技能选择过程如何设置 `skill`、`COOLDOWN_TICKS`、`cnt1` 和领域扩张状态；
2. 过程是否要求玩家实体、原版咒术师实体或特定能力/效果；
3. 领域创建与结束流程由哪些 tick 事件或其他过程持续推进；
4. 对自定义 Heian Sukuna 实体调用时，原版过程是否存在空指针、错误伤害归属或重复生成风险。

状态：方法签名与部分字节码依赖已核实；运行时兼容性、编译和游戏内行为未验证。
