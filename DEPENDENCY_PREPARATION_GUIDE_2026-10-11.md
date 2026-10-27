# ForgeGradle + Minecraft 依赖准备说明

## 本项目的依赖坐标

| 组件 | 项目配置 |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.26` |
| ForgeGradle 插件 | `net.minecraftforge.gradle`，版本范围 `[6.0,6.2)` |
| 映射 | `official:1.20.1` |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4` |
| Gradle Wrapper | `8.8` |

这些配置来自 `gradle.properties`、`build.gradle`、`settings.gradle` 和 `gradle/wrapper/gradle-wrapper.properties`。

## 自动准备依赖

- Linux/macOS：在项目根目录运行 `bash tools/prepare_forge_minecraft_dependencies.sh`
- Windows PowerShell：在项目根目录运行 `powershell -ExecutionPolicy Bypass -File .\tools\Prepare-ForgeMinecraft-Dependencies.ps1`

脚本会运行 Gradle Wrapper，并执行 `dependencies compileJava`，让 ForgeGradle 下载/生成编译所需的 Forge、Minecraft 映射和开发依赖。日志保存在 `build/dependency-setup/prepare-dependencies.log`。

## 重要限制

该脚本是依赖解析器，不是把第三方二进制库伪装成已打包的离线依赖。首次执行需要访问 Gradle Plugin Portal、Forge Maven、Minecraft 相关下载端点以及 GeckoLib Maven。成功后，Gradle 会把实际下载的依赖保存在 `GRADLE_USER_HOME`（默认 `~/.gradle`）缓存中。

当前执行环境访问 `maven.minecraftforge.net` 时 DNS 解析失败（`curl: (6) Could not resolve host`），并且没有检测到已缓存的 ForgeGradle/Minecraft 开发依赖。因此这里无法诚实地生成完整离线依赖仓库，也没有声称已完成 Java 编译。请在可联网的电脑上运行上述脚本；如果你需要真正可搬运的离线包，应在联网环境完成一次依赖解析后，再整体打包对应的 Gradle 用户缓存，并在目标机器上用 `--offline` 验证。
