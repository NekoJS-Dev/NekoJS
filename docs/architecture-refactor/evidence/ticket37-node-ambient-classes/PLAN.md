# Node 模块 ambient class 反馈环

基线 `fbb3ea4ae7e6a9c43f59281a78d0ac8249788e1b`；生产源码仍为 `2a421c18`。实际完整 manual 的 Blob 和 EventEmitter（普通名及 node: 别名）包含四个 TS1038。先保留原始严格 TSC 与真实生成器的最小 class RED，再排序解释并只改共享源码，不改生成输出、默认 includes、skipLibCheck 或手写 manual 生产器。

主 agent 独占编辑、执行及证据；两轴审查只读。需要同时保留构造器、泛型、静态字段/方法、实例字段/方法、继承和模块别名的类型消费者能力。其他完整 IDE 错误继续保留 FAIL；性能、多人视觉和维护者结论不能由此推断。

实际默认 includes/skipLibCheck=false 的主节点重复检查保留942错误/4条TS1038；真实生成器 class 回归1test FAIL，严格双别名caller2条TS1038，非法caller5错误（含3条真实非法类型）。确认RED后按顺序提出：重复修饰符、模块消费不匹配、泛型/继承头提取错误。只修改重复修饰符后18focusedtests PASS，严格caller0，非法caller仍3；真实builtin从精确旧JAR提取并与新生成器比较，除4个修饰符外字节完全相同，其严格编译18→14错误，其他错误保留。

源码提交f84e2adb；五节点4742tests/271skip/801XML/0fail/directexit0，109tasks/2m59s；构建后平台门槛及npm类型fixtures PASS。实机辅助脚本默认四节点误用已保留完成日志和独立四节点summary，随后补26.2并独立保留原summary后合并全部五节点；五个服务端正常RCON/directexit0/十端口free。原逐字比较遇到声明排序差异的FAIL保留；下一步按声明作用域保留类体/overload顺序核对，不能把重新排序输出宣称逐字相同。精确f84性能按预声明两组reload和一组startup全部样本验收，完成前不推断PASS。
