;(function () {
  // NekoJS 自动 JSX runtime：与 NekoJsxCompiler 的 automatic 模式配套
  //（import { jsx, jsxs, Fragment } from 'nekojs/jsx-runtime'）。
  // 纯 JS 实现、不依赖宿主对象：元素是普通对象 { $$nekoJsx, type, key, props, children }，
  // 渲染/消费由用户侧代码（或后续的 painter/渲染绑定）自行处理。

  const FRAGMENT: symbol = Symbol('nekojs.jsx.fragment')

  interface NekoJsxProps {
    [key: string]: unknown
    children?: unknown
  }

  interface NekoJsxElement {
    $$nekoJsx: true
    type: unknown
    key: string | null
    props: NekoJsxProps
    children: unknown[]
  }

  function createElement(type: unknown, props: NekoJsxProps | null, key: string | null): NekoJsxElement {
    const merged: NekoJsxProps = {}
    if (props != null) {
      for (const name of Object.keys(props)) {
        if (name !== 'key') merged[name] = props[name]
      }
    }
    const rawChildren = merged.children
    const children: unknown[] = rawChildren === undefined ? [] : Array.isArray(rawChildren) ? rawChildren : [rawChildren]
    return { $$nekoJsx: true, type: type, key: key, props: merged, children: children }
  }

  function jsx(type: unknown, props: NekoJsxProps | null, key?: string | number): NekoJsxElement {
    return createElement(type, props, key === undefined ? null : String(key))
  }

  function jsxs(type: unknown, props: NekoJsxProps | null, key?: string | number): NekoJsxElement {
    return createElement(type, props, key === undefined ? null : String(key))
  }

  globalThis.__nekoNodeDefine(['nekojs/jsx-runtime'], { jsx: jsx, jsxs: jsxs, Fragment: FRAGMENT })
})()
