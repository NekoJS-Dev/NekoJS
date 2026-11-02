# 精准 API 查询的一手设计参考

日期：2026-10-31

性质：维护者调研笔记，中文供审阅；不是随模组发布的 AI 手册。仅核对公开一手资料，不修改产品代码，不运行构建或测试。以下采用建议均为设计判断，不代表 NekoJS 已实现相关查询。

## 1. 总结

推荐组合：**受 Schema 约束的结构化过滤 + 明确的关联对象作用域 + 字段选择 + 受限关系展开 + 绑定快照的分页**。

可借鉴成熟 API 的具体机制，不需要直接实现整个 OData、引入 Elasticsearch，或把一个面向文本检索的过滤语法当精确类型查询语言。

最重要的正确性风险是：类有两个重载时，参数条件来自重载 A，返回值条件却来自重载 B，最后错误地报告存在一个满足全部条件的调用。增加参数数量并不能自动解决这种关联错误。

## 2. Google AIP-160：过滤字段、类型和语法校验

官方事实：AIP-160 建议列表/搜索操作采用一个结构化语法的字符串 filter，明确字段遍历、逻辑运算和类型校验；非法字段或字段值应返回 INVALID_ARGUMENT。其规范同时说明列表过滤有模糊匹配/排序特征，且 OR 比 AND 优先。它还不允许按数组下标查询 repeated 字段。因此它不是直接满足“第 0 个参数类型精确匹配”的现成协议。

来源：[Google AIP-160 — Filtering](https://google.aip.dev/160)。

对 NekoJS 的建议：

- 采用允许字段清单、字段类型、枚举值与操作符校验；未知参数不能静默忽略。
- 发布查询能力和 Schema，使 AI 能先确认支持什么。
- AI 主接口优先用 JSON 结构表达 all/any/not，避免字符串优先级和转义歧义。
- 若后续提供便于人工输入的 filter 字符串，解析到同一内部 AST，并明确其是否兼容 AIP-160，不能只借名字宣称兼容。

## 3. Google AIP-158：从第一版就提供分页

官方事实：AIP-158 要求集合查询从开始就提供分页，使用 page_size、page_token 和 next_page_token。后续页面除 page_size 外应保持查询参数一致；页 token 应不透明，且不能充当资源授权。精确总数不是必需字段，估算总数需说明。

来源：[Google AIP-158 — Pagination](https://google.aip.dev/158)。

对 NekoJS 的建议：

- 初版即提供有限默认页大小和 continuation；不要先返回所有方法，再以不兼容变更加入截断。
- token 绑定规范化后的过滤、排序、投影和 snapshot，避免换世界/换索引后拼接不同结果。
- 返回每页采用的 snapshotId、覆盖范围和下一页；没有下一页只表示已遍历该查询范围，不代表整个运行时 API 都被完整采集。
- token 每次请求仍需正常鉴权。绑定 snapshot 是本项目设计补充，不是 AIP-158 自带的在线世界一致性保证。

## 4. Microsoft Graph：把过滤、字段选择和关系展开分开

官方事实：Microsoft Graph 将 filter 用于选记录、select 用于选属性、expand 用于附带关联资源，并另外提供排序和分页。官方特别说明不同操作和版本支持的查询参数可能不同。

来源：[Microsoft Graph — Customize responses with query parameters](https://learn.microsoft.com/en-us/graph/query-parameters)。

对 NekoJS 的建议：

- filter 决定哪些类/成员符合要求；select 决定只返回 id、签名、说明还是来源；expand 决定是否返回 declaringType、输入适配或继承信息。
- 每种实体公布允许的字段和关系；拒绝不支持的组合，而不是返回看似成功的不完整条件结果。
- 展开需要深度、数量和字段上限，避免查询一个类就返回整棵 Java 继承/依赖图。
- 可以采用上述职责划分，不必实现整个 OData URL 语言或引入 Graph 依赖。

## 5. Elasticsearch bool：精确过滤与文本相关度分开

官方事实：bool 查询提供 must、should、filter 和 must_not；其中 filter 不贡献相关度分数。should 的默认 minimum_should_match 会随是否存在 must/filter 条件而变化，因此它不总是“至少满足一项”的硬过滤。

来源：[Elasticsearch — Boolean query](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-bool-query)。

对 NekoJS 的建议：

- 精确字段条件决定是否匹配，关键词相关度只决定探索性搜索的排序，不能用高分补偿不满足的硬条件。
- 自有 JSON 查询若叫 any，应明确至少满足一个；不要原样搬入 should 的条件性默认值。
- 请求精确模式时不自动降级为模糊匹配。无结果可另返回建议，但建议不能混入精确结果。
- 这是语义参考，不构成引入 Elasticsearch 服务或 Lucene 依赖的理由。

## 6. Elasticsearch nested：同一重载/参数对象的条件必须相关

官方事实：Elastic 演示普通对象数组扁平化会丢失对象内部字段关联，进而产生跨元素假匹配；nested 查询把一个数组元素作为独立匹配范围，并可通过 inner_hits 返回实际匹配元素。nested 中的内层否定表示存在一个不满足条件的子对象，不能替代“没有任何子对象满足条件”的外层否定。

来源：[Elasticsearch — Nested field type](https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/nested)、[Elasticsearch — Nested query](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-nested-query)。

对 NekoJS 的必要推论：

```text
f(String) -> int
f(ItemStack, int) -> boolean
```

查询“一个参数、参数是 String、返回 boolean”必须得到零个重载。不能让第一条满足参数条件、第二条满足返回值条件，然后报告方法名 f 匹配。

需要明确量词和范围：

- overload.anyMatch：存在一个重载，其内部全部条件成立。
- parameter.anyMatch：在同一个重载内，存在一个参数同时满足位置/类型/可选性等条件。
- not(overload.anyMatch(...))：不存在符合条件的重载。
- overload.anyMatch(not(...))：存在不符合某条件的重载；与上一种含义不同。

以上名称仅作语义示意，由主方案冻结最终字段。首版也可把查询实体直接定义为单个 overload，减少关联层级；方法组通过 groupId 聚合展示。

## 7. 建议作为原型验收的反例

这些是本项目推导的测试建议，不是一手规范对 NekoJS 的要求：

1. 同名重载的参数数目与返回值跨重载拼接，必须拒绝假匹配。
2. 同一个参数的 position 和 type 不能分别来自两个参数。
3. 简名相同但全限定名不同的类型必须返回歧义或准确候选。
4. 含任意/全部/否定的数组条件有不同真值表，包括空数组。
5. 换 snapshot 后继续旧 cursor，不得静默切到新结果集。
6. select 裁剪结果字段不改变匹配逻辑；expand 不改变根实体筛选，除非显式关系谓词要求。
7. 模糊搜索结果不能出现在严格查询的 matches 中。
8. 未支持的泛型可赋值关系查询返回 unsupported，不退化为字符串 contains。
9. 缺少结构化声明来源时报告 incomplete，不以空列表证明该 API 不存在。

## 8. 边界与未采用项

本轮没有必要再引入 LSP/SCIP 协议栈。它们可用于后续符号标识与导航互操作研究，但上述过滤/关联/分页问题已经可以用这些一手参考明确约束。

本笔记不选择 HTTP 路由、最终 JSON Schema、存储引擎、查询规划器或 MCP 工具名称；这些应由产品设计统一决定。真实 Graal 调用适配和重载选择也不能由索引中的静态类型关系直接保证。
