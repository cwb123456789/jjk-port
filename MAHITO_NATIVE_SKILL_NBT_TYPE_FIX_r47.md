# r47 — 修复真人原生技能 ID 的 NBT 类型读取

## 根因

JujutsuCraft v50.1 的原生技能过程（包括 `CursedTechniqueMahitoProcedure`）从实体持久化数据读取 `skill` 时使用 `CompoundTag.getDouble("skill")`。旧版桥接和状态诊断命令却调用 `getInt("skill")`。NBT 数值类型不匹配时，`getInt` 对 DoubleTag 返回默认整数值，导致领域技能 1520 可能始终无法被桥接识别。

## 修复

- `NativeMahitoFormBridge` 改为 `getDouble("skill")`，与原版技能过程的存储类型一致。
- `PrototypeCommands.inspectNativeMahito` 同步改为读取 double，避免诊断命令错误报告 `skill=0`。
- 扩展专项审计，防止将来退回整数读取。

## 验证边界

静态审计和源码类型核对可以确认调用方式已修正；仍需 Java 编译和游戏内测试确认事件触发、纹理同步与动画播放。
