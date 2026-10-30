# Third-party notices / 第三方资源声明

本项目自主源码依据根目录 `LICENSE` 所载 GPL-3.0 发布。第三方软件、模型、平台服务和可选运行组件分别适用其原始许可或服务条款；主项目许可不改变这些权利边界。公开仓库：<https://github.com/cmijohnson/zhenglian-zhixie-source>。

本源码仓库提供业务实现、接口契约、数据库迁移、集成补丁、构建配置和测试。第三方库由 Maven、npm、Cargo、Gradle 或 Swift Package Manager 获取，版本以各模块锁文件和构建配置为准。安装目录、模型权重、运行镜像、平台账号、访问凭据与签名材料不随本源码包分发。

## QQ Bridge、QCE 与 NapCat

### 自研 QQ Bridge

`cloud-java/qq-bridge` 中的实例管理、路由控制、会话桥接和清理逻辑是本项目源码，按主项目 GPL-3.0 提供。依赖 Dockerode 5.0.1（Apache-2.0）、Express 5.2.1 和 ws 8.21.3（MIT）。

### QQ Chat Exporter

- 上游：<https://github.com/shuakami/qq-chat-exporter>
- 固定源码 revision：`58fc533f1bfdbf4535be0f83af4e595d4d7ecf27`
- 许可：GNU General Public License v3.0，原文见 <https://github.com/shuakami/qq-chat-exporter/blob/58fc533f1bfdbf4535be0f83af4e595d4d7ecf27/LICENSE>。
- 集成修改：`cloud-java/deploy/qce-patches/apply.mjs` 及同目录补丁提供路径、Session/CSRF、网关路由、会话查询与客户同步适配。构建脚本在上游源码获取后应用这些修改。
- 归属与对应源码：构建过程保留上游许可证并生成修改声明。分发修改后的 QCE 镜像时，按 GPL-3.0 提供该 revision 的对应源码和项目补丁；可用 `QCE_SOURCE_ARCHIVE_DIR` 指定对应源码归档输出目录。

### NapCat

NapCat 是独立的可选 QQ 运行组件。官方许可为 **Limited Redistribution License for NapCat**，见 <https://github.com/NapNeko/NapCatQQ/blob/main/LICENSE>。该许可包含非商业用途、归属和再分发条件，修改代码的公开分发及额外权利受其条款约束。NapCat 不能被标注为 GPL 或 MIT，也不随主项目重新许可。

本仓库不包含 NapCat 源码或镜像。构建脚本通过 `QCE_RUNTIME_BASE_IMAGE` 选择外部运行镜像；默认标签 `mlikiowa/napcat-docker:latest` 表示运行输入，不能替代不可变的版本或镜像 digest。使用者按对应许可取得运行组件，并记录实际使用版本；商业使用或超出条款的用途应取得权利人授权。

## 关键软件依赖

| 范围 | 组件与版本 | 许可声明 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.5.16、Framework 6.2.19、Security 6.5.11、Session 3.5.7 | Apache-2.0 |
| 迁移与数据库连接 | Flyway Community 12.11.0；PostgreSQL JDBC 42.7.11 | Apache-2.0；BSD-2-Clause |
| 接口与文件 | springdoc 2.8.9、Commons CSV 1.14.1、POI 5.4.1、SQLite JDBC 3.50.3.0 | Apache-2.0；SQLite引擎为公共领域 |
| 密码组件 | Bouncy Castle Provider 1.81 | Bouncy Castle Licence |
| AI与协议SDK | MCP SDK 1.1.2；LangChain4j Core 1.18.0 | MIT；Apache-2.0 |
| 飞书Java SDK | oapi-sdk 2.7.3 | Apache-2.0，以精确版本LICENSE及Maven POM为依据 |
| Web框架 | Vue 3.5.40、Vue Router 4.6.4、Pinia 3.0.4 | MIT |
| 主站与论坛构建 | TypeScript 5.9.3；Vite 7.3.6；Vue插件6.0.8；vue-tsc主站3.3.7/论坛3.3.8 | Apache-2.0；其余MIT |
| 前端测试 | Vitest 3.2.7、Vue Test Utils 2.4.11、jsdom 26.1.0 | MIT |
| 后端测试 | Spring测试组件；Testcontainers 1.21.4 | Apache-2.0；MIT |
| 容器与代理 | Docker Engine/Compose；Nginx 1.29系列；Node.js 22系列 | Apache-2.0；BSD-2-Clause；MIT及各自所含组件许可 |
| 数据存储 | PostgreSQL 18.4；pgvector 0.8.6 | PostgreSQL License |
| React桌面 | React/React DOM 19.2.7、React Router 7.18.0、clsx 2.1.1、Day.js 1.11.21 | MIT |
| 电子表格 | SheetJS xlsx 0.18.5；rust_xlsxwriter 0.95.0 | Apache-2.0；MIT OR Apache-2.0 |
| Tauri | Rust tauri2.11.3、JS API2.11.1及锁定插件 | MIT OR Apache-2.0 |
| Rust基础库 | anyhow、chrono、serde、serde_json、log；csv；rusqlite | MIT OR Apache-2.0；csv为Unlicense或MIT；rusqlite为MIT |
| Android | Kotlin2.0.21、AndroidX/Compose、Retrofit2.11.0、OkHttp4.12.0、Coroutines1.9.0 | Apache-2.0 |
| 移动端构建测试 | Gradle8.9、AGP8.7.3；JUnit4.13.2 | Apache-2.0；EPL-1.0 |
| Swift客户端 | Swift；ZIPFoundation0.9.20 | Apache-2.0 WITH Runtime Library Exception；MIT |

以上列出直接依赖和关键运行组件。传递依赖以精确锁文件、Maven解析结果及各组件自身的 LICENSE/NOTICE 为准。源码包通过构建工具获取依赖；分发已编译程序或镜像时，保留适用的版权、许可、NOTICE、修改声明与对应源码。Apple SDK、Android SDK、Docker Desktop 等开发环境产品按其独立条款取得。

## 嵌入模型与外部服务

- **Ollama**：项目软件采用 MIT。Compose 固定软件镜像 digest `sha256:1685741456770df6e3cceb2a945a5f75e020f658d1701509668d6f4688f1dd3f`，软件许可与所下载模型许可分别适用。
- **BGE-M3**：官方模型卡 <https://huggingface.co/BAAI/bge-m3> 声明 MIT。仓库使用配置名 `bge-m3` 并提供嵌入接口适配；模型权重由运行环境取得，实际权重或模型manifest版本在运行记录中登记。
- **生成模型 API**：`AI_BASE_URL`、`AI_MODEL`、`AI_API_KEY` 指向运行者选择的 OpenAI-compatible 服务。接口兼容不意味着模型权重开源；账户、密钥、数据处理与使用权限适用实际提供商条款。
- **飞书与企业微信**：平台服务遵循各自开发者协议。公开的是本项目的身份映射、业务处理、回调与消息集成代码，平台账号、应用凭据和服务资源不包含在GPL授权中。

## 合成数据与复现

固定种子 `20260927` 的生成器、验证脚本和业务测试由项目提供。合成记录与运行日志用于复现数据结构、引用关系和软件测试；在线模型结果、外部账号登录和第三方服务调用由对应运行记录单独说明。生成数据的结构、用途与授权见仓库的数据说明。
