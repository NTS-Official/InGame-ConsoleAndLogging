# 工作区开发约定

本文件适用于整个工作区，补充全局 `AGENTS.md`。子目录有更具体的约定时，在其范围内执行子目录约定。

## 项目结构与边界

本项目使用 Architectury API，在 Fabric 和 NeoForge 上开发同一个 Minecraft Mod。

- `common/`：跨加载器共享的功能、领域规则和公共资源。
- `fabric/`：Fabric 入口、平台接入与加载器元数据。
- `neoforge/`：NeoForge 入口、平台接入与加载器元数据。
- `docs/`：整理后的项目用法与结构文档。
- `gradle/`、根目录 Gradle 文件与 Wrapper：构建配置和依赖管理。

平台模块通过 Gradle 依赖 `common`；`common` 不得反向依赖平台模块，两个平台模块不得互相依赖。共享功能放入 `common`，平台入口只负责接入，平台差异留在各自模块。

`common` 中已有的 Fabric Loader 依赖仅用于环境注解，不得引入其他 Fabric Loader API。客户端类与服务端可加载的公共代码保持边界，公共初始化不得加载客户端专用类。

依赖与 Mod 版本以 `gradle.properties` 为准，Java 编译目标以根目录 `build.gradle` 为准，Gradle 版本以 Wrapper 配置为准。三个模块已配置 Kotlin JVM 插件，Java 与 Kotlin 编译目标统一取根构建的 `javaTarget`。

## Kotlin 与代码风格

新增和实质修改的业务代码尽量使用 Kotlin，采用 Kotlin 惯用写法。按语义使用属性、空安全、数据类、顶层函数和 `object`，避免照搬 Java 的 getter/setter、静态工具类和冗余包装。

Fabric 使用 Fabric Language Kotlin 提供运行时，NeoForge 将 Kotlin 标准库通过 Loom `include` 嵌入发布用 `shadowJar`。修改 Kotlin 依赖时同步检查编译插件、平台运行时版本和加载声明，保持 JVM 目标一致；Java 互操作或框架限制确有需要时保留 Java。

优先通过命名、类型和清晰的控制流表达逻辑，尽量少写注释。仅为代码无法直接表达的必要约束或特殊行为添加简短注释，不添加复述实现的注释。

## Mixin 与注入

Mixin 注入优先使用 MixinExtras 提供的操作注解，例如 `@WrapOperation`、`@ModifyExpressionValue`、`@ModifyReceiver`、`@WrapWithCondition` 等，不使用 `@Redirect` 等旧式替换注解。只有 MixinExtras 无法表达目标语义时才使用旧方法，并在改动附近说明原因。

## 修改与决策

收到功能需求或遇到测试失败时，先检查整体职责、数据流和现有抽象，定位问题根源。优先选择符合长期结构的调整，必要时重构、合并或删除已有实现，避免默认追加分支、状态、配置、兼容层和重复实现。

改动保持在任务范围内，不顺手改造无关代码。跨加载器功能同时检查两个平台的依赖、入口和资源声明。

## 文档

整理后的开发与使用文档统一放入 `docs/`；本文件作为工作区指令保留在根目录。

文档只写具体用法、操作步骤、配置说明和项目结构，不写需求来源、对话经过、用户要求、决策来龙去脉或“因为某事所以这样做”的解释。

同一项事实只维护一份。文档引用构建配置或对应文件，避免重复维护依赖版本等易变信息。

## 测试与验证

尽量避免为假设性风险添加防御性测试，不为简单、可逆的改动引入测试框架或额外测试体系。确有必要的测试围绕实际业务行为或可复现缺陷编写。

测试与业务代码明确分离，放在对应模块的测试源码集中，例如 `src/test/kotlin/`；测试依赖、测试替身和测试专用资源不得进入生产源码或发布产物。当前项目尚未配置专门的测试依赖。

根据改动选择相关模块的编译、构建或测试；涉及共享代码与跨加载器集成时，验证 Fabric 和 NeoForge 两端。测试失败先定位原因并修正设计或实现，不靠跳过测试、削弱断言或无依据地重复运行解决。

仅修改文档时无需构建。交付时如实说明已执行的验证和未验证的部分。

## 构建与产物

使用项目自带的 Gradle Wrapper。Windows PowerShell 常用命令：

```powershell
.\gradlew.bat tasks --all
.\gradlew.bat build
.\gradlew.bat :fabric:build
.\gradlew.bat :neoforge:build
```

开发运行与其他任务以 Wrapper 列出的实际任务为准。构建需要与项目 Java 目标匹配的 JDK，新增 Kotlin 编译目标须与其一致。

当前使用 `dev.architectury.loom-no-remap`，平台模块通过 `shadowJar` 打包共享代码。发布用 Mod JAR 位于对应平台的 `build/libs/`，没有 `dev` 分类后缀；不要将带 `dev` 或 `sources` 分类的 JAR 当作发布产物，也不要默认套用 `remapJar` 流程。
