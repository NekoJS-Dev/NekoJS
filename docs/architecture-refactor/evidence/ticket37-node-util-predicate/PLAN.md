# Node util 类型谓词反馈环

基线10a58679，生产源码f84e2adb及其精确性能PASS已归档。实际新JAR默认完整TSC仍有两条TS1225：util/node:util 的 isProxy 参数叫 _value，返回谓词却引用不存在的 value。继续先保留真实canonical资源→NodeModuleTypeDocs→严格caller RED，不修改生成输出、默认 includes 或安全设置。

需要保持 isProxy 既有返回false行为、参数名及类型谓词能力；先区分canonical资源注解错误、生成器改名错误、消费者导入错误。修复后要检查擦除得到的运行时JS字节、真实Node模块行为、完整矩阵和精确JAR。主agent独占编辑与执行；审查可只读。不得由局部通过认证完整IDE或总票据完成。自动续跑PAUSED，维护者NOTRECORDED，无发布授权。
