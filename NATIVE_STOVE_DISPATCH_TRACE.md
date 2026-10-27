# 原版火炉技能调度追踪补充

## 本轮检查

- 对随项目提供的 JujutsuCraft v50.1 JAR 中 3,493 个 class 文件做常量池扫描，查找对 `AIFlameArrowProcedure` 的直接类引用。
- 扫描到的直接引用目前只有 `net/mcreator/jujutsucraft/entity/FlameArrowEntity.class`；这说明不能仅凭方法名假设宿傩 AI 直接调用该过程。原版可能通过通用调度/反射路径调用，单纯常量池扫描无法排除这种可能。
- `AIFlameArrowProcedure` 的当前随附 JAR 字节码使用 `skill`、`cnt1` 状态及原版 `FlameArrowEntity`。本轮没有把其他反编译输出中的技能编号当作当前 JAR 的已证实事实。

## 工程决定

暂不修改运行时桥接，也不伪造一个未经证实的技能编号写入自定义实体。后续需要继续追踪原版通用 AI 分派入口、技能号赋值位置以及动画/冷却状态设置，再考虑复用原版过程。

## 复现

```bash
python3 tools/audit_native_stove_dispatch.py
```

这是静态字节码调查，不代表 Java 编译或游戏内测试通过。
