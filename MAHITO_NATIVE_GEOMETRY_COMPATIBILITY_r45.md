# r45 — 原生真人骨骼兼容性核对

## 核对结论

对比基础 JujutsuCraft JAR 的 `assets/jujutsucraft/geo/human.geo.json` 与移植资源 `assets/jujutsuenhancement/geo/mahito_stage2.geo.json` 后，二者**不是可直接互换的骨骼结构**。

- 直接同名骨骼仅有 `body`、`torso`、`head`。
- 原生骨骼包含 `right_arm`、`left_arm`、`right_leg`、`left_leg`、`itemMainHand`、`itemOffHand` 及护甲骨骼。
- 移植骨骼使用 `rightArm`、`leftArm`、`rightLeg`、`leftLeg`、`rightItem`、`leftItem`、`root`、`bone4`、`bone5` 和 locator 骨骼。

## 对实现的影响

不能只把原生模型资源路径替换成 `mahito_stage2.geo.json`：骨骼名称、层级、物品挂点及护甲骨骼不一致，现有原生动画会有缺失或错位风险。纹理桥接和 `setAnimation("domain_expansion1")` 仍使用 JujutsuCraft 原生模型/动画；它们**不代表** Bedrock 二阶段模型动画已经接入。

## 下一阶段实施要求

1. 为真人二阶段建立独立 GeoModel/Renderer，并在客户端按原生真人的同步纹理或专用同步形态标记选择模型。
2. 将二阶段动画中的骨骼名映射到实际几何骨骼，核验旋转枢轴、层级与手持物定位器。
3. 在完成资源适配前，不应将 Bedrock 动画 ID 直接传给原生 `setAnimation`，因为原生模型读取的是 `jujutsucraft:animations/human.animation.json`。

本次仅新增静态骨骼对比审计与报告，没有声称已完成渲染器替换、Java 编译或游戏内验证。
