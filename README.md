# Slay the Spire Auto API

杀戮尖塔 1 本地 HTTP API mod。此独立仓库包含 API 源码、测试、构建脚本和编译文件，不包含世界线搜索器。

## 安装

1. 安装游戏并订阅 ModTheSpire。
2. 将本目录的 `SlayTheSpireAutoAPI.jar` 放入游戏 `mods` 目录。
3. 通过 ModTheSpire 启动游戏并启用 Slay the Spire Auto API。
4. 访问 `http://127.0.0.1:8080/api/state?pretty=1` 查看 JSON 状态。

API 没有游戏内界面。游戏未运行或 mod 未加载时，HTTP 服务不可用。

## 功能

- 读取血量、能量、各卡牌堆、敌人、意图、能力、遗物和药水等实时状态。
- `/api/action` 将操作提交给游戏线程执行。
- 快照及检查点需要额外安装兼容的 SaveStateMod；普通状态读取不需要该依赖。
- 动作和检查点恢复接口会改变游戏状态，本服务并非纯只读。
- API 不执行世界线搜索，也不保证搜索结果最优。

具体路由和响应字段见 `src/main/java/stsapi/ApiServer.java` 与 `GameState.java`。
状态读取示例见 `example_client.py`。

## 构建

需要 JDK 8 或更新版本，在 PowerShell 中运行：

```powershell
.\build.ps1 -JdkHome 'C:\path\to\jdk' -GameDir 'D:\path\to\SlayTheSpire'
```

输出为 `build/SlayTheSpireAutoAPI.jar`。找到真实游戏依赖时，脚本还会安装到游戏 `mods` 目录。
找不到游戏依赖时使用 `stubs/` 进行离线编译检查，不能代替游戏内验证。
根目录 jar 为已有的 1.4.0 构建产物；游戏内完整兼容性仍需验证。

## 文件

- `src/main/`：API 实现与 mod 元信息。
- `src/test/`：离线冒烟测试。
- `stubs/`：测试与离线编译桩，不打包到 mod。
- `SlayTheSpireAutoAPI.jar`：安装文件。
- `build/`、`releases/`：本地构建与旧产物，不提交。

仓库不包含游戏本体和第三方 mod 的 jar。
