# SRCon Mod — Minecraft 群服互通客户端

Minecraft 1.20.1 **Forge 服务端 Mod**，作为「QQ群 ↔ Minecraft 服务器」双向互通的客户端：
连接 AstrBot 插件的 WebSocket 服务端，上报游戏事件，接收并执行远程命令。

## 功能
- ✅ 服务器启动/停止自动连接/断开 WebSocket
- ✅ 认证握手（token + server_id）
- ✅ 事件上报：游戏聊天、玩家进出服、死亡
- ✅ 接收远程命令并在服务器执行（/srcon 指令）
- ✅ 断线自动重连（10s）
- ✅ 零外部依赖（使用 JDK 内置 `java.net.http.WebSocket`）

## 环境变量配置
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SRCON_SERVER_ID` | `s1` | 服务器唯一标识（对应 AstrBot 侧白名单/转发配置） |
| `SRCON_SERVER_NAME` | `生存服` | 服务器显示名称 |
| `SRCON_WS_URL` | `ws://127.0.0.1:8766`（8765 常被 mineastr 等插件占用） | AstrBot 插件 WebSocket 地址 |
| `SRCON_TOKEN` | `srcon_default_token` | 与 AstrBot 插件 config.yaml 中 `token` 一致 |

## 编译（无 Gradle，方案A：直接 javac）
```bash
# 依赖服务器 libraries（首次启动服务器后生成）
CP=$(find /path/to/server/libraries -name '*.jar' | tr '\n' ':')/path/to/server/java21.jar
javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options -proc:none \
      -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
# 打包
mkdir -p build/jar/META-INF
cp src/main/resources/META-INF/mods.toml build/jar/META-INF/
cp src/main/resources/pack.mcmeta build/jar/
cp -r build/classes/com build/jar/
jar cf srcon-1.0.0.jar -C build/jar .
```

> 注意：Mohist/Forge 运行时的 Minecraft 类是 **SRG 混淆名**（如 `m_129892_`），代码直接使用 SRG 名编译，
> 需配合 `server-*.srg.jar`（位于服务器 `libraries/net/minecraft/server/`）编译。

## 演进与配套
- AstrBot 插件：[astrbot-plugin-ptsrcon](https://github.com/Zaienscookie/astrbot-plugin-ptsrcon)
- 通信协议：见 `docs/protocol.md`（开发中）
