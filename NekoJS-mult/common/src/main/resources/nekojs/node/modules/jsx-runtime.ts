;(function () {
  // NekoJS 自动 JSX runtime：与 NekoJsxCompiler 的 automatic 模式配套
  //（import { jsx, jsxs, Fragment } from 'nekojs/jsx-runtime'）。
  //
  // 产出**不可变 VNode**：record 形态的对象在创建时冻结，props 与 children 都是新的
  // 拷贝，调用者无法通过返回值改写已创建的 VNode（ticket 40 AC1）。VNode 只持有
  // type/props/children/key 等脚本侧数据，不持有 Minecraft、loader、GuiGraphics、
  // Font、Screen、GL 或任何长期可变 host 对象。
  //
  // 消费侧：core/ui 的 ScriptUiVNodeFactory 识别 $$nekoJsx 标记与 kind/type/props 形状，
  // 把元素树转换为候选 VNode；函数组件（大写标签）展开为候选树由 reconciler 负责。
  //
  // 本文件是"脚本式 .ts"（IIFE + __nekoNodeDefine），类型注解由 NekoTypeScriptCompiler
  // 擦除后求值——因此只使用可擦除的类型标注，不写对象字面量类型、计算属性名或枚举。

  const FRAGMENT = Symbol('nekojs.jsx.fragment')
  const ELEMENT_MARKER = '$$nekoJsx'

  function freezeProps(source) {
    const merged = {}
    if (source != null) {
      for (const name of Object.keys(source)) {
        if (name !== 'key') merged[name] = source[name]
      }
    }
    return Object.freeze(merged)
  }

  function childrenOf(props) {
    const raw = props.children
    if (raw === undefined) return Object.freeze([])
    return Object.freeze(Array.isArray(raw) ? raw.slice() : [raw])
  }

  // 注意：这里刻意不用 cond ? a : bareIdentifier 形态——NekoTypeScriptCompiler 的擦除器
  // 会把三元表达式的裸标识符 else 分支误判成类型注解并删掉（既有擦除器限制，已记入本票
  // closure record）。用 if/return 等价表达规避。
  function resolveType(type) {
    if (type === FRAGMENT) return 'Fragment'
    return type
  }

  function resolveKey(key) {
    if (key === undefined) return null
    return String(key)
  }

  function createElement(kind, type, props, key) {
    const merged = freezeProps(props)
    const children = childrenOf(merged)
    let element = {}
    element[ELEMENT_MARKER] = true
    element.kind = kind
    element.type = resolveType(type)
    element.key = resolveKey(key)
    element.props = merged
    element.children = children
    return Object.freeze(element)
  }

  function jsx(type, props, key) {
    return createElement('jsx', type, props, key)
  }

  function jsxs(type, props, key) {
    return createElement('jsxs', type, props, key)
  }

  globalThis.__nekoNodeDefine(['nekojs/jsx-runtime'], { jsx: jsx, jsxs: jsxs, Fragment: FRAGMENT })
})()