# Recipe 捕获原型：全部未投产

绑定引擎 `187d5051`，没有修改产品、golden、运行时、白名单或依赖规则。额外 Java fixture 和已缓存 DFU/Guava/fastutil 只经临时 init-script 加入本次 Test classpath，未添加仓库依赖。生产完整严格 TS 基线仍是15c4d9a6的1029，F6仍488条。

## 已取得真实红证

简单 Codec/Result 夹具只重现过宽编码输入，不能重现继承冲突；改用真实 DFU 10.0.21 MapCodec/Codec/DataResult 并补齐其缓存依赖后，公开 backend 产生12条错误：TS2430×1、TS2344×5、TS2416×2、TS2322×2、TS2345×1、TS2578×1。错误链与实际 Recipe.getSerializer→codec→codec.comapFlatMap 同形。除显式排除且有同一不透明 fixture 的 java.time 两类外，没有不可用依赖。第一次 init-script 未排除 buildSrc、首次 DFU 缺依赖的设置失败日志保留，不算产品红证。

完整面保留的候选使用独立副本，不改生成基线或生产输出：

| 输入 | tsc退出 | 诊断 | 结论 |
|---|---:|---|---|
| 简化夹具 | 2 | TS2578×1 | 不能证明已复现继承根因 |
| 真实DFU基线 | 2 | 上述12条 | 合法继承和泛型调用失败；任意父对象编码漏检 |
| 加强控制基线 | 2 | 原12条+TS2578×1 | 独立捕获的交叉编码也漏检 |
| 不透明标记+完整祖先facet | 2 | TS2578×1 | 消除本夹具继承错误并保留多数正负合同，但合并了相同上界的独立捕获 |
| never参数+facet | 2 | TS2578×2、TS2339×1 | 跨捕获漏检、泛型读取泄漏never，可伪造窄值，直接读取还不可用 |

不透明方案的错误仅在加强控制第36行：两个静态类型相同的父级 provider 产生不同捕获，把第二个解码值交给第一个编码器仍被接收。`never` 方案第42行还允许泛型读取结果赋给任意窄类。不能只以清空继承错误把这些方案写成已完成，也没有证明新的有作用域调用方案，尚未加入任何辅助 API。

Java编译器对照：泛型 helper 内同一捕获的编解码、从 wildcard serializer 调用该 helper 均exit 0；跨 provider 的编码调用exit 1，明确报告独立 CAP#1/CAP#2。这不只是 TypeScript 用例的主观期望。没有执行 Minecraft 或修改性能记录。

## 重放

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-27-recipe-capture-prototypes/verify.py --typecheck --java
```

五组 TypeScript 冻结输入、原始语义诊断、fixture源码、编译类、Java正负源文件、生产器设置失败/成功日志和变体脚本均有摘要。TypeScript固定5.8.3；Java对照使用本机JDK21及摘要绑定的DFU jar，可用 `--dfu-jar` 指定等价路径。重放成功表示精确重现失败，不代表候选可以投产。

F6保持开放。下一步先修可复用完整 StreamView/返回facet的F8，保留本处完整读写和捕获身份问题继续设计；不引入全局协变、any或成员删除。
