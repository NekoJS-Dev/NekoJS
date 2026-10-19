# 预检固定正则的生命周期收口

## 已实现

`GlobalBindingMemberValidator` 的七个声明扫描正则及一个标识符正则原样提升为 private static final Pattern。正则内容、import 的 MULTILINE 标志、其余默认 flags、扫描顺序、捕获组和全部诊断规则不变；每次扫描仍调用 matcher 创建局部 Matcher，没有共享输入、匹配位置或可变结果。

旧实现每次有效 validate 会编译七次固定声明模式；每命中一个 import 子句或解构片段，再编译一次同样的标识符模式，共 7 + I + D 次。新实现每个类加载器只编译八个模式一次。只读 Luna 调查确认所有模式都是固定字面量，没有动态 pattern；EventCallbackSourceValidator 没有同类调用，保持原样。主代理负责修改。

依据是[已有 root 阶段诊断](../architecture-refactor/evidence/2026-10-20-current-reload-stages/README.md)中出现 Pattern 编译/study 栈，以及当前源码中直接可见的重复编译。采样次数不能换算为独占耗时，尚未测量本改动的分配或墙钟收益。

Pattern 只保存固定规则，由 JVM 类初始化安全发布；Matcher 与源级名字集合仍为每次校验私有。没有引入源码、AST 或跨代诊断缓存，不保留 Context、路径、schema 或用户输入。固定正则若将来被误改成无效表达式，会在类初始化时报错；这与原先首次扫描时失败的时机不同，不能掩盖该错误。

## 保留的执行入口预检

另一次限定只读调查确认，ScriptExecutor 的入口原文预检与冷 JS 管线检查存在重叠，但不能直接删除：每次 executeEntry 会先清理入口错误，同 session prepared 命中则跳过管线预检；删入口预检会使缓存命中后错误面板丢失诊断。跨代 transform 命中仍会使用当前 schema 校验，不能将两种命中混为一谈。

入口预检不受 scriptMemberValidation 控制、校验原文并使用 callback error 路由；管线受开关控制，且转译语言检查生成的 JS。共享源码还必须绑定最终执行使用的 trust/path 批准快照，不能把入口额外读取的字符串直接喂给准备缓存以省去批准和读取。为消除一次读取而新增这套协议、改变诊断阶段/次数或给 AST 增加跨调用生命周期，尚无收益依据，本阶段没有采用。发现的转译入口原文预检问题没有在性能改动中顺带改变。

本轮还只读重查了既有本地 JFR 的 JavaErrorThrow：Server thread 的二十个 HostContext$1 来自每次 HostContext 构造时创建的 StackOverflowError 哨兵，不能据事件名判为二十次运行错误。没有修改 Graal 或捕获/丢弃错误。

## 验证边界

遵循用户先完成实现的要求，本阶段没有运行测试、编译、游戏或新性能测量；完成源码、调用方与 diff 人工检查，git diff --check 通过。当前正式结论仍是 24d35a5d 的重载 289.4 / 338.0 ms 均 FAIL、启动 36661.0 ms PASS。

已有 GlobalBindingMemberValidatorTest 的 importAndCatchAndGlobalsAreNotFlagged、namedFunctionDeclarationAndCallAreNotFlagged 覆盖 import/标识符提取/普通 catch 和 function 声明；class、解构、for-of/in 及 catch 可选捕获组非空分支缺少明确独立用例。本次是固定规则生命周期调整，没有补只检查字段或表达式文字的测试。集中验证须覆盖既有成员/组合预检、当前 schema 变化及绑定安装四项新回归，再进行固定语料的前后对照；原正式采样器和门槛保持，不能以本组件通过代替总体性能通过。
