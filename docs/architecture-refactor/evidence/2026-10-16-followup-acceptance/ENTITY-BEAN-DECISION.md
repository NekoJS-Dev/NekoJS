# Entity Bean 声明迁移：五处精确范围已批准

状态：2026-10-17 用户明确回复「批准这五处精确迁移」。已按下面五处 Entity TS 属性及必要的精确贡献机制完成实施和验证，结果见 [实际报告](ENTITY-BEAN.md)；不是整票或发布的维护者验收。

## 已确认的问题和精确范围

五节点的 Entity 都把 Java `isAttackable(): boolean` 额外投影为 TS `get attackable(): boolean`，而 LivingEntity 的真实 Java 方法 `attackable(): boolean` 在 TS 中是可调用成员。同名 boolean 属性和方法不能构成一致的结构子类型；把标量说成可调用、丢弃泛型实参或放宽 bound 均不能正确解决。

已对泛型适配器批次新增的全部诊断作只读匹配：930 次计数中，926 次沿 `attackable` 链展开；其余 4 次来自两个 26.1.2 节点的 MinecartFurnace.push 字段与父类 push 方法冲突。server/startup 的新增记录重复，按节点和诊断身份去重后为 342 条记录、465 次计数。这里不是说本提案可以消除全部现存 TSC 错误。

建议仅移除下列五个生成声明中的 **Entity 自身派生 Bean 属性**：

| 节点 | 目标 | 保留的真实方法 |
|---|---|---|
| NeoForge 1.21.1 | Entity.attackable 属性 | Entity.isAttackable() |
| NeoForge 26.1.2 | Entity.attackable 属性 | Entity.isAttackable() |
| NeoForge 26.2.0 | Entity.attackable 属性 | Entity.isAttackable() |
| Fabric 26.1.2 | Entity.attackable 属性 | Entity.isAttackable() |
| Fabric 26.2.0 | Entity.attackable 属性 | Entity.isAttackable() |

LivingEntity/ArmorStand 的真实 attackable() 方法保留。调查中另有 24 处独立同名 Bean alias，不属于本提案；不按属性名全局过滤。MinecartFurnace.push 不纳入本次决策。

## 用户可见变化

```ts
// Before: a value typed as Entity exposes both forms.
entity.attackable;
entity.isAttackable();

// Proposed TypeScript usage after the declaration migration.
entity.isAttackable();
livingEntity.attackable();
```

以 Entity 类型使用的脚本，原有 `.attackable` 属性补全和类型检查将消失，应改用 `.isAttackable()`。Java/Graal 运行时仍保留原有成员访问，不改变脚本执行、Java 方法、Python 输出、持久数据或 wire。其他类自己声明的独立 Bean alias 继续按原规则生成；这不是运行时属性删除。

## 已批准的实现机制

现有 `probe.modify_type.hideMethod` 会同时隐藏方法并影响共享 IR/Python，不能用于这一 TS 专属迁移；整类手写 Entity 声明也会丢失自动反射维护。已有 TypeDocs 贡献链可作为落点：

1. 扩展 canonical TypeDocs 贡献条目，表达精确目标 Class、getter Java 名、Bean 属性名的 TS 投影排除。延续 TypeDocsPoint / Plugin Hook / catalog 的现有收集和冻结规则；生成的配对材料通过原流程再生成，完整核对构造/快照兼容调用方。
2. TypeScriptProbeBackend 将排除项交给 TypeScriptClassRenderer，仅对精确匹配的 Bean alias 使用已有“保留真实方法、略去属性”的分支，不更改 MethodDecl.hidden 或共享类型事实。
3. 在 Minecraft-facing 的共享版本/节点插件贡献中用本地 Entity.class 注册 `isAttackable → attackable`。common 不硬编码 Minecraft 类名，也不扩大扫描闭包。
4. 增加定向严格 TSC 证明 LivingEntity 可以作为 Entity bound 的类型实参；分别验证父 getter 的真实调用、子类真实方法和其余 alias 保留。增加贡献收集/清理、节点配对和生成产物检查。
5. 在 wiki 写明仅 TS 的属性迁移表，再跑 common:check、处理器、五节点官方 JAR 构建/实际输出、完整默认 IDE 检查和增量双轴复核。如出现新的契约变化，先记录，不扩大此批准范围。

批准前没有直接等价的精确排除接口；新增贡献条目只为上述具体需求。实现保留现有快照构造器形状，并经收集、冻结、复用及五节点实际输出验证，详见实际报告。

## 为什么需要决定

[AGENTS.md](../../../../AGENTS.md)要求保留明确迁移范围之外的公共行为和契约；[Script API 约定](../../../agents/coding.md#public-changes)要求已有公有名称在迁移授权内才能改变。这五处 TS 属性的删除属于新的公开声明迁移，先前 A15/A16/A22 的批准不包含它。

用户已批准以上五处精确范围。按此实施并如实记录完整 TSC 结果；不扩大到其他同名 alias 或 push 冲突，不关闭票 28、37 或把草稿交接标成发布完成。
