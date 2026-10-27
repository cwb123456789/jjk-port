# r46 — 真人二阶段资源骨骼名称映射

## 本次实际新增

- `assets/jujutsuenhancement/geo/mahito_stage2_native.geo.json`
- `assets/jujutsuenhancement/animations/mahito_stage2_native.animation.json`
- `tools/audit_native_mahito_stage2_bone_mapping.py`

把移植资源中常用的 `rightArm/leftArm/rightLeg/leftLeg/rightItem/leftItem` 名称转换为 JujutsuCraft 原生常用名称 `right_arm/left_arm/right_leg/left_leg/itemMainHand/itemOffHand`，动画轨道执行相同重命名。原始移植文件保持不变。

## 限制

这不是可直接替换原生 `human.geo.json` 的完整模型。移植骨架仍缺少原版护甲骨骼，且保留 `root`、`bone4`、`bone5` 与 locator 骨骼；其父子关系、枢轴、比例及动画轨道还需运行时验证。新增资源当前尚未由原生真人渲染器引用，因此不能声称二阶段模型/动画已在游戏内生效。

## 验证

`tools/audit_native_mahito_stage2_bone_mapping.py` 检查 JSON 可解析、关键骨骼映射、名称唯一性及动画轨道名称一致性。该静态检查不替代 Java 编译或游戏内测试。

## 动画表达式转换注意事项

Bedrock 动画文件含有 GeckoLib 静态关键帧格式无法表达的 Molang 字符串表达式。为确保生成的候选资源通过项目的数值关键帧校验，转换文件把这些字符串标量替换为 `0`。这会损失依赖目标朝向/查询变量的动态动作；因此此文件只能用于后续适配与测试，不能视为保真动画移植。原始 `mahito_compatible.animation.json` 未被修改。
