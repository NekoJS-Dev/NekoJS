/**
 * 示范 node 模块（TypeScript 写法）。
 *
 * <p>演示 .ts 模块的类型注解如何被自动提取为补全声明：
 * <ul>
 *   <li>加载时，NekoTypeScriptCompiler.eraseTypescript 擦除类型注解（interface/type/参数与返回类型），
 *       enum/namespace 降级为 IIFE，运行时与 .js 模块等价（脚本风格 IIFE + __nekoNodeDefine）；</li>
 *   <li>补全声明由 NodeModuleTypeDocs.extractTS 从本源码自动生成
 *       （实现与声明同源，无需手写 declare module 'node:example'）。</li>
 * </ul>
 *
 * <p>脚本上下文不支持 ES export/import，故模块用 IIFE + __nekoNodeDefine 注册，
 * 顶层声明用类型注解标注，导出对象用简写引用它们。
 */
;(function () {
  interface ExampleInfo { id: number; name: string }
  type Status = 'ok' | 'error';

  /** 根据 id 构造示例信息。 */
  function getInfo(id: number): ExampleInfo {
    return { id, name: 'item-' + id }
  }

  /** 返回当前状态。 */
  function status(): Status {
    return 'ok'
  }

  const version: string = '1.0.0'

  const example = {
    getInfo,
    status,
    version
  }

  globalThis.__nekoNodeDefine(['example', 'node:example'], example)
})()
