# r72 审查（对照基岩版源码）

r72 是别的 AI 在 r71 基础上做的一轮改动。逐条对照基岩版附加包后的结论：

| r72 改动 | 结论 | 原因 |
|---|---|---|
| `HollowPurpleEntity` 加方块射线检测，碰到方块就停下、爆炸、消失 | **不采纳，已保持原样** | 基岩版 `scripts/index.js` 里 `ds:shootprojectile` 每 tick 直接 `teleport` 到 `位置 + 速度`，没有任何方块判断，200 tick 后才移除。茈是穿过去的，地形由函数（如 `4x4sphere`）每 tick 挖掉。加射线检测会让茈在没被挖掉的第一个方块前爆炸，走向和基岩版相反。这是把早期（r58）版本的行为又加回来，而 r58 的检查脚本本身就是照那个旧行为写的。 |
| 五条悟普通茈改播 `animation.gojo.purple_cutscene`（22.975 秒） | **不采纳，已保持 `purple_normal`** | `limitless/limitless5b.mcfunction` 在 `move=480` 播的是 `animation.hollowpurple`（24 秒），对应 `purple_normal`。`animation.limitless_purple`（22.975 秒，即 `purple_cutscene`）是另一个片段，不是这个技能用的。技能总时长 480 tick 也和 24 秒对得上。 |
| `gojo_awakened.animation.json` 的关键帧从 `post` + `catmullrom` 改成 `vector` + `linear` | **不采纳，已保持原样** | 原格式是 Blockbench / 基岩版的标准写法，GeckoLib 能读，以前的版本已在游戏里正常播放过。改成线性会丢掉平滑插值（r72 日志自己也写了有 1,016 个关键帧被压成线性），收益只是让一个自写的检查脚本通过。检查脚本已改成同时接受 `vector`、`post`、`pre` 和裸数组。 |
| 检查脚本改读当前的 `GojoAwakenedEntity` | 采纳（已去掉「必须用 purple_cutscene」和「必须是 vector」两条） | 旧脚本检查的是已停用的原型代码。 |
| Mahito / Sukuna 诊断脚本多找一个 jar 路径 | 未合入 | 只对那台机器的目录有用；这几个检查针对已停用的「改原模组」桥接。 |

## 教训

1. `tools/` 里的静态检查只是对源码做文字匹配，**通过不等于和基岩版一致**。判断行为对不对，只能看 mcfunction 和脚本。检查脚本和基岩版源码冲突时，改脚本。
2. 现在 33/37 通过；剩下 4 个需要原模组 jar 放在 `/mnt/data/`，是针对已停用桥接的，不影响这个模组。
3. r72 没有编译，也没有进游戏测试（它自己的日志也这么写）。
