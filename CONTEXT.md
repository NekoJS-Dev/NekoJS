# NekoJS

基于 GraalVM/GraalJS 的 Minecraft 脚本魔改引擎：让整合包作者用现代 JS/TS 在多版本、多加载器上写启动/服务端/客户端脚本。

## Language

**脚本 API（Script API）**:
面向脚本作者（整合包作者）的公开 JS 接口。写法简洁好用优先；重大重构允许一次性 breaking，配 wiki 迁移表（报错保持普通形式，不带修复指引）。
_Avoid_: 用户 API、前端接口

**插件 API（Plugin API）**:
面向 Java 插件开发者的接口与扩展契约；活跃开发期允许 breaking change。
_Avoid_: 开发者 API

**扩展点（Extension Point）**:
插件向引擎注入某一类能力的命名挂载点（如注册表信息、注册表对象类型）。
_Avoid_: 钩子、插桩点

**贡献面（Contributor）**:
扩展点向插件暴露的贡献接口；插件实现该接口即被此扩展点收集。
_Avoid_: 钩子接口、SPI

**插件钩子（Plugin Hook）**:
`NekoJSPlugin` 基接口上面向作者的门面方法（ADR-0010 双形态模型）：注册面（收集型，bootstrap 期配对收集）与回调面（直调型，事件时平台触发）。与扩展点是投影关系——钩子必有配对的扩展点，反之不必然。基接口的"钩子"一词专指此概念，不与扩展点混称。
_Avoid_: 用"钩子"指代扩展点或贡献面本身

**扩展点句柄（Extension Handle）**:
扩展点注册时返回的产物持有对象，bootstrap 完成后凭它获取该扩展点的产物。
_Avoid_: 产物引用、provider

**通用注册表（Generic Registry）**:
由注册表元信息与对象类型工厂统一驱动的注册系统，目标是取代逐类型手写包装。
_Avoid_: 泛型注册、注册中心

**连带注册（Co-registration）**:
注册一种对象时按约定隐式带出的关联注册，如 Block→BlockItem、Fluid→流体方块与桶。
_Avoid_: 自动注册、隐式注册

**版本树（Version tree）**:
stonecutter 管理的多版本共享源码树，平台差异以守卫与 replacements 表达。
_Avoid_: 共享树、主干
