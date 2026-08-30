# Empurple

> 基于 [Purple Framework](https://github.com/chuxuehaocai/purple-framework) 的 Kotlin/JVM QQ 机器人插件。
>
> Empurple 是 [Yorushiro](https://github.com/NightcordSekai/Yorushiro) 的重构版本，主要面向 maimai DX 数据查询、Best 50 展示和 LLM 交互。项目仍处于持续整理阶段，代码结构和行为可能继续变化。

## 功能

- 通过 OneBot 风格协议接收和发送群聊、私聊消息
- maimai DX 账号二维码认证与 QQ 用户绑定
- 查询用户资料并生成 `whoami` 图片
- 获取并生成 Best 50 图片，同时发布可访问的网页
- 查询 maimai DX 功能票数量，并支持下发功能票
- 使用 LLM 进行群聊提及回复和 Best 50 实力评价
- 提供只读 WebUI，用于查看框架状态、插件和缓存
- 自动缓存头像、曲绘、音乐数据和生成结果

## 环境要求

- JDK 21
- 可用的 Gradle Wrapper（项目已包含）
- 一个可正常运行的 [Purple Framework](https://github.com/chuxuehaocai/purple-framework) 配置
- 如使用 maimai 相关功能，需要可访问对应的 Aime/称号服服务
- 如启用 LLM 功能，需要兼容 Anthropic Messages API 的服务地址和 API Key

项目使用 Gradle 9.3.0 Wrapper，Kotlin JVM 版本为 2.3.21。

## 构建

Linux、macOS、Git Bash：

```bash
./gradlew build
```

Windows PowerShell：

```powershell
./gradlew.bat build
```

仅编译主代码：

```bash
./gradlew compileKotlin
```

运行测试：

```bash
./gradlew test
```

清理构建产物：

```bash
./gradlew clean
```

## 运行

直接使用 Gradle 运行：

```bash
./gradlew run
```

Windows：

```powershell
./gradlew.bat run
```

应用入口是 `RunFrameworkKt`，它会启动 Purple Framework。`run` 任务会先构建插件，并将生成的 JAR 复制到当前项目的 `plugins/` 目录，然后由框架加载。

也可以先构建插件，再由 Purple Framework 的运行方式启动。当前项目依赖本地的 Purple Framework composite build，且 `run` 任务会自动准备插件目录，因此推荐使用 `./gradlew run`。

## Purple Framework 依赖

`settings.gradle.kts` 默认通过 Gradle composite build 引入上级目录中的 Purple Framework：

```text
../purple-framework
```

因此，若本地开发两个项目并排放置，目录结构应类似：

```text
workspace/
├── empurple/
└── purple-framework/
```

Purple Framework 仓库地址：

<https://github.com/chuxuehaocai/purple-framework>

如果 Purple Framework 已发布到 Maven 仓库，也可以将 `settings.gradle.kts` 中的 `includeBuild("../purple-framework")` 改为对应的远程依赖配置。

## 配置

Purple Framework 和 Empurple 都通过框架的配置管理器自动创建配置文件。首次启动后，请查看运行目录下的 `configs/` 目录，并填写 Empurple 对应的配置文件：

```text
configs/empurple-config.json
```

框架自身的连接配置通常位于：

```text
configs/purple-framework-config.json
```

具体协议、地址、端口和 OneBot 连接方式请参考 Purple Framework 文档。

### Empurple 配置项

下面列出 `EmpurpleConfig` 中的主要配置项。配置文件由程序自动补全，实际字段名以生成的 JSON 为准。

| 配置项 | 说明 |
| --- | --- |
| `titleServerUrl` | maimai DX 称号服 API 根地址 |
| `aesKey` | 称号服通信使用的 AES Key |
| `aesIv` | 称号服通信使用的 AES IV |
| `aimeUrl` | Aime Server 地址，用于解析二维码字符串 |
| `keychipId` | maimai 服务所需的 Keychip ID |
| `obfuscateParam` | API 名称混淆参数 |
| `apiVersion` | 称号服 API 版本，对应请求头 `Mai-Encoding` |
| `clientId` | 客户端 ID |
| `aimeSalt` | Aime 相关 Salt |
| `regionId` / `regionName` | 区域 ID 和名称 |
| `placeId` / `placeName` | 机台地点 ID 和名称 |
| `webUiEnabled` | 是否启用只读 WebUI，默认 `false` |
| `webUiHost` | WebUI 监听地址，默认 `127.0.0.1` |
| `webUiPort` | WebUI 监听端口，默认 `8080` |
| `webUiToken` | WebUI Bearer Token；监听非回环地址时必须设置 |
| `llmEnabled` | 是否启用 LLM 功能，默认 `true` |
| `llmMentionPrefix` | 触发 LLM 回复的消息前缀，默认是一个 QQ @ 消息段 |
| `llmBaseUrl` | LLM API 根地址，默认指向 DeepSeek Anthropic 兼容接口 |
| `llmApiKey` | LLM API Key |
| `llmModelName` | 使用的模型名称 |
| `llmTemperature` | LLM temperature |
| `llmSystemPrompt` | 普通 LLM 对话的系统提示词 |
| `llmMaxTokens` | 单次 LLM 回复最大 Token 数 |
| `llmMaxToolIterations` | 单次请求最多执行的工具循环次数 |
| `llmMaxHistoryTurns` | 每个用户保留的对话轮数，设置为 `0` 禁用历史 |
| `llmModerationEnabled` | 是否启用 LLM 二次安全审核 |
| `llmModerationSystemPrompt` | 二次安全审核提示词 |
| `llmModerationBlockedReply` | 审核拦截时发送给用户的文本 |

至少需要根据实际服务填写称号服、Aime Server 和客户端相关字段。不要把包含密钥、Token 或账号信息的配置文件提交到公开仓库。

## 内置命令

命令前缀为 `/`。命令通常在群聊中触发，二维码认证流程会要求用户转到私聊发送内容。

| 命令 | 用法 | 说明 |
| --- | --- | --- |
| `bind` | `/bind` | 通过二维码解析出的 `SGWCMAID` 字符串绑定 QQ 用户和 maimai UID |
| `b50` | `/b50` | 获取 Best 50 数据，生成图片并发布网页；已绑定用户可直接使用 |
| `whoami` | `/whoami` | 获取用户资料并生成资料图片 |
| `ticket` | `/ticket` | 不带参数查询功能票；使用 `/ticket 1` 到 `/ticket 5` 下发对应功能票 |
| `看看实力` | `/看看实力` | 根据已绑定用户的 Best 50 数据生成 LLM 实力评价 |
| `jrrp` | `/jrrp` 或 `/今日人品` | 查看当天稳定、次日刷新的今日人品分数 |

### Best 50 流程

1. 在群内发送 `/bind`。
2. 按提示私聊发送二维码解析出的完整 `SGWCMAID...` 字符串。
3. 绑定成功后，在群内发送 `/b50`。
4. 机器人会获取数据、生成图片，并发送网页地址。

如果不进行绑定，也可以直接发送 `/b50`，然后按提示在两分钟内私聊发送二维码解析字符串。

### Ticket 流程

- `/ticket`：认证后查询当前功能票数量。
- `/ticket 1` 至 `/ticket 5`：认证后尝试下发指定 Ticket。

下发流程会登录称号服并等待约 60 秒，再执行写入操作。请确认服务配置正确，并只对获得用户明确授权的账号使用该功能。

## WebUI

将以下配置打开即可启用只读面板：

```json
{
  "webUiEnabled": true,
  "webUiHost": "127.0.0.1",
  "webUiPort": 8080
}
```

启动后访问：

```text
http://127.0.0.1:8080/
```

WebUI 提供以下只读接口：

- `/api/health`：健康状态和运行时间
- `/api/status`：框架连接状态和插件数量
- `/api/plugins`：已加载插件
- `/api/cache`：缓存文件统计

当 `webUiHost` 绑定非回环地址时，必须设置 `webUiToken`，请求时使用：

```http
Authorization: Bearer <your-token>
```

WebUI 不提供命令执行或运行状态修改接口，但仍建议仅在可信网络中开放，并使用强 Token。

## 数据目录

程序会在运行目录自动创建以下目录：

```text
resource/
├── icons/       # maimai 头像缓存
├── CoverCache/  # 曲绘缓存
└── DataCache/   # 音乐数据、生成图片等缓存
```

这些目录可能包含用户数据或缓存内容，请按部署环境决定是否备份和清理。

## 项目结构

```text
src/main/kotlin/
├── RunFramework.kt                  # 应用入口
└── dev/naominet/empurple/
    ├── EmpurplePlugin.kt            # 插件生命周期和消息监听
    ├── command/                     # 命令系统及内置命令
    ├── config/                      # Empurple 配置模型
    ├── llm/                         # LLM 请求、历史和工具调用
    ├── maimai/                      # maimai API、请求和 DTO
    ├── utils/                       # 图片、缓存、加密和数据工具
    └── web/                         # 只读 WebUI
src/main/resources/                  # 图片模板和 Best 50 资源
```

## 开发说明

- 使用 Kotlin 官方代码风格。
- 主代码使用 JVM 21 编译。
- 新增内置命令时，实现 `ICommand` 并在 `CommandManager` 中注册。
- 修改称号服协议时，优先保持请求构造、AES/Zlib 编解码和重试逻辑的边界清晰。
- 提交前建议执行：

```bash
./gradlew clean build test
```

项目目前仍是重构中的个人/实验性机器人项目。请在真实账号和生产群组中谨慎启用涉及登录、账号数据读取、功能票写入和远程 LLM 的功能。

## 许可证

当前仓库未声明明确的开源许可证。未经项目维护者确认，请不要默认按照 MIT 或其他许可证再分发。

## 致谢

- [Purple Framework](https://github.com/chuxuehaocai/purple-framework)
- [Yorushiro](https://github.com/NightcordSekai/Yorushiro)
