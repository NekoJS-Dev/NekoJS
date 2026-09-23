;(function () {
  type NekoUiPrimitive = 'screen' | 'panel' | 'row' | 'column' | 'stack' | 'scroll' | 'label' | 'button' | 'input' | 'image' | 'spacer'
  type NekoUiJsxType = NekoUiPrimitive | NekoUiComponent | symbol
  type NekoUiLayoutSize = number | 'auto' | 'fill'
  type NekoUiEventName = 'onClick' | 'onRelease' | 'onScroll' | 'onKey' | 'onTextInput' | 'onFocus' | 'onBlur' | 'onChange' | 'onSubmit'

  interface NekoUiEvent {
    readonly type: string
    readonly target: string
    readonly x?: number
    readonly y?: number
    readonly button?: number
    readonly key?: string
    readonly value?: string
    readonly delta?: number
  }

  interface NekoVNode {
    readonly $$nekoJsx: true
    readonly type: string | symbol | NekoUiComponent
    readonly key: string | null
    readonly props: Readonly<Record<string, unknown>>
    readonly children: readonly unknown[]
  }

  type NekoUiComponent = Function
  type NekoUiCallback = Function

  interface NekoUiDiagnostic {
    readonly rootId: string
    readonly phase: 'render' | 'component' | 'layout' | 'event' | 'host-update'
    readonly error: unknown
  }

  interface NekoUiHostNode {
    readonly type: NekoUiPrimitive | '#text'
    readonly key: string | null
    readonly props: Readonly<Record<string, unknown>>
    readonly children: readonly NekoUiHostNode[]
  }

  interface NekoUiHostTransaction {
    create(type: NekoUiPrimitive | '#text', key: string | null, props: Readonly<Record<string, unknown>>): unknown
    update(handle: unknown, type: NekoUiPrimitive | '#text', key: string | null, props: Readonly<Record<string, unknown>>): void
    order(parent: unknown | null, children: readonly unknown[]): void
    remove(handle: unknown): void
    commit(roots: readonly unknown[]): void
    rollback(): void
  }

  interface NekoUiHostAdapter {
    isOwnerThread(): boolean
    enqueue(action: NekoUiCallback): boolean
    supportsPrimitive(type: NekoUiPrimitive): boolean
    layout(tree: readonly NekoUiHostNode[]): void
    begin(): NekoUiHostTransaction
    reportDiagnostic(diagnostic: NekoUiDiagnostic): void
  }

  interface NekoUiSignal<T> {
    get(): T
    set(value: T): boolean | 'queued'
    update(updater: NekoUiCallback): boolean | 'queued'
  }

  interface NekoUiStore {
    get(key: string): unknown
    set(key: string, value: unknown): boolean | 'queued'
    update(key: string, updater: NekoUiCallback): boolean | 'queued'
    snapshot(): Readonly<Record<string, unknown>>
  }

  interface NekoUiRootHandle {
    readonly id: string
    refresh(): boolean | 'queued'
    dispatch(id: string, eventName: string, event: Partial<NekoUiEvent>): boolean | 'queued'
    isDisposed(): boolean
    close(): boolean | 'queued'
  }

  interface NekoUiPrimitiveProps {
    id?: string
    children?: unknown
    title?: string
    pausesGame?: boolean
    closeOnEscape?: boolean
    background?: string | number
    borderColor?: string | number
    borderWidth?: number
    radius?: number
    scrollX?: boolean
    scrollY?: boolean
    scrollOffset?: number
    text?: string
    color?: string | number
    fontSize?: number
    wrap?: boolean
    disabled?: boolean
    tooltip?: string
    value?: string
    placeholder?: string
    maxLength?: number
    resource?: string
    fit?: 'contain' | 'cover' | 'stretch'
    width?: NekoUiLayoutSize
    height?: NekoUiLayoutSize
    minWidth?: number
    maxWidth?: number
    minHeight?: number
    maxHeight?: number
    gap?: number
    padding?: number
    align?: 'start' | 'center' | 'end' | 'stretch'
    justify?: 'start' | 'center' | 'end' | 'spaceBetween' | 'spaceAround'
    visible?: boolean
    onClick?: NekoUiCallback
    onRelease?: NekoUiCallback
    onScroll?: NekoUiCallback
    onKey?: NekoUiCallback
    onTextInput?: NekoUiCallback
    onFocus?: NekoUiCallback
    onBlur?: NekoUiCallback
    onChange?: NekoUiCallback
    onSubmit?: NekoUiCallback
  }

  interface NekoUiRootOptions {
    id?: string
  }

  interface NekoUiApi {
    primitives(): readonly NekoUiPrimitive[]
    element(type: NekoUiPrimitive | NekoUiComponent | symbol, props: NekoUiPrimitiveProps | null, key?: string | number): NekoVNode
    createSignal<T>(initial: T): NekoUiSignal<T>
    createStore(initial: Record<string, unknown>): NekoUiStore
    createRoot(render: NekoUiCallback, adapter: NekoUiHostAdapter, options?: NekoUiRootOptions): NekoUiRootHandle
    batch<T>(callback: () => T): T
    readonly fragment: symbol
  }
  const FRAGMENT: symbol = Symbol('nekojs.jsx.fragment')
  const PRIMITIVES = Object.freeze({
    screen: ['title', 'pausesGame', 'closeOnEscape'],
    panel: ['background', 'borderColor', 'borderWidth', 'radius'],
    row: [],
    column: [],
    stack: [],
    scroll: ['scrollX', 'scrollY', 'scrollOffset'],
    label: ['text', 'color', 'fontSize', 'wrap'],
    button: ['text', 'disabled', 'tooltip'],
    input: ['value', 'placeholder', 'maxLength', 'disabled'],
    image: ['resource', 'fit'],
    spacer: []
  })
  const SHARED_PROPS = Object.freeze([
    'id', 'width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight',
    'gap', 'padding', 'align', 'justify', 'visible'
  ])
  const EVENT_PROPS = Object.freeze([
    'onClick', 'onRelease', 'onScroll', 'onKey', 'onTextInput',
    'onFocus', 'onBlur', 'onChange', 'onSubmit'
  ])
  const DIMENSIONS = new Set(['width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight'])
  const ALIGNMENTS = new Set(['start', 'center', 'end', 'stretch'])
  const JUSTIFICATIONS = new Set(['start', 'center', 'end', 'spaceBetween', 'spaceAround'])
  const TEXT_CHILDREN = new Set(['label', 'button'])
  let activeTracker = null
  let batchDepth = 0
  const pendingRoots = new Set()
  let nextRootId = 1
  const signalObservers = new WeakMap()

  function fail(phase, message, cause) {
    const detail = cause == null ? '' : ': ' + (cause.message || String(cause))
    const error = new Error(message + detail)
    if (cause != null) error.cause = cause
    error.uiPhase = phase
    return error
  }

  function isVNode(value) {
    return value != null && typeof value === 'object' && value.$$nekoJsx === true
  }

  function freezeValue(value, seen) {
    if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value
    if (typeof value === 'function') return value
    if (isVNode(value) && Object.isFrozen(value)) return value
    if (typeof value !== 'object') throw new TypeError('VNode values must be script data or callbacks')
    if (seen.has(value)) throw new TypeError('VNode props cannot contain cycles')
    seen.add(value)
    let result
    if (Array.isArray(value)) {
      result = Object.freeze(value.map(item => freezeValue(item, seen)))
    } else {
      const prototype = Object.getPrototypeOf(value)
      if (prototype !== Object.prototype && prototype !== null) {
        throw new TypeError('VNode props cannot retain host objects')
      }
      result = Object.create(null)
      for (const name of Object.keys(value)) result[name] = freezeValue(value[name], seen)
      Object.freeze(result)
    }
    seen.delete(value)
    return result
  }

  function normalizeKey(key) {
    if (key == null) return null
    if (typeof key === 'string') return key
    if (typeof key === 'number' && Number.isFinite(key)) return String(key)
    throw new TypeError('JSX key must be a string, finite number, or null')
  }

  function createElement(type, props, key) {
    if (typeof type !== 'string' && typeof type !== 'function' && type !== FRAGMENT) {
      throw new TypeError('JSX type must be a registered primitive, component function, or Fragment')
    }
    const merged = Object.create(null)
    if (props != null) {
      if (typeof props !== 'object' || Array.isArray(props)) throw new TypeError('JSX props must be an object or null')
      for (const name of Object.keys(props)) {
        if (name !== 'key') merged[name] = props[name]
      }
    }
    const resolvedKey = normalizeKey(key === undefined ? (props == null ? null : props.key) : key)
    const frozenProps = freezeValue(merged, new WeakSet())
    const rawChildren = frozenProps.children
    const children = rawChildren === undefined ? [] : Array.isArray(rawChildren) ? rawChildren : [rawChildren]
    return Object.freeze({
      $$nekoJsx: true,
      type: type,
      key: resolvedKey,
      props: frozenProps,
      children: Object.freeze(children.slice())
    })
  }

  function jsx(type: NekoUiJsxType, props: NekoUiPrimitiveProps | null, key?: string | number): NekoVNode {
    return createElement(type, props, key)
  }

  function jsxs(type: NekoUiJsxType, props: NekoUiPrimitiveProps | null, key?: string | number): NekoVNode {
    return createElement(type, props, key)
  }

  function plainObject(value) {
    return value != null && typeof value === 'object' && !Array.isArray(value)
      && (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null)
  }

  function finiteNonNegative(value, name) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
      throw fail('layout', name + ' must be a finite non-negative number')
    }
  }

  function validateLayout(props) {
    for (const name of DIMENSIONS) {
      const value = props[name]
      if (value === undefined || value === null || value === 'auto' || value === 'fill') continue
      finiteNonNegative(value, name)
    }
    for (const name of ['gap', 'padding']) {
      if (props[name] !== undefined && props[name] !== null) finiteNonNegative(props[name], name)
    }
    for (const name of ['minWidth', 'maxWidth', 'minHeight', 'maxHeight']) {
      const value = props[name]
      if (value === undefined || value === null) continue
      finiteNonNegative(value, name)
    }
    if (props.minWidth != null && props.maxWidth != null && props.minWidth > props.maxWidth) {
      throw fail('layout', 'minWidth must not exceed maxWidth')
    }
    if (props.minHeight != null && props.maxHeight != null && props.minHeight > props.maxHeight) {
      throw fail('layout', 'minHeight must not exceed maxHeight')
    }
    if (props.align != null && !ALIGNMENTS.has(props.align)) throw fail('layout', 'Invalid align value: ' + props.align)
    if (props.justify != null && !JUSTIFICATIONS.has(props.justify)) throw fail('layout', 'Invalid justify value: ' + props.justify)
    if (props.visible != null && typeof props.visible !== 'boolean') throw fail('layout', 'visible must be a boolean')
  }

  function validateProps(type, props) {
    if (!Object.prototype.hasOwnProperty.call(PRIMITIVES, type)) {
      throw fail('layout', 'Unknown JSX primitive: ' + type)
    }
    const allowed = new Set([...SHARED_PROPS, ...PRIMITIVES[type], 'children'])
    if (TEXT_CHILDREN.has(type)) allowed.add('id')
    for (const name of Object.keys(props)) {
      if (name === 'key') continue
      if (EVENT_PROPS.includes(name)) {
        if (props[name] != null && typeof props[name] !== 'function') {
          throw fail('layout', name + ' must be a function or null')
        }
        continue
      }
      if (!allowed.has(name)) throw fail('layout', 'Unsupported prop for ' + type + ': ' + name)
    }
    validateLayout(props)
    if (props.id != null && (typeof props.id !== 'string' || props.id.length === 0)) {
      throw fail('layout', 'id must be a non-empty string')
    }
    if (props.disabled != null && typeof props.disabled !== 'boolean') throw fail('layout', 'disabled must be a boolean')
    if (type === 'input' && props.value != null && typeof props.value !== 'string') throw fail('layout', 'input.value must be a string')
    if (type === 'input' && props.maxLength != null
      && (!Number.isInteger(props.maxLength) || props.maxLength < 0)) {
      throw fail('layout', 'input.maxLength must be a non-negative integer')
    }
  }

  function callGuest(callback, args) {
    if (typeof callback === 'function') return callback(...args)
    if (callback != null && typeof callback.execute === 'function') return callback.execute(...args)
    throw new TypeError('Expected a callable script function')
  }

  function hostPropsFor(type, props, children) {
    const hostProps = Object.create(null)
    for (const name of Object.keys(props)) {
      if (name === 'children' || EVENT_PROPS.includes(name)) continue
      hostProps[name] = props[name]
    }
    if (TEXT_CHILDREN.has(type) && children.length > 0) {
      if (children.some(child => typeof child !== 'string' && typeof child !== 'number')) {
        throw fail('layout', type + ' children must be text')
      }
      hostProps.text = children.map(String).join('')
      children = []
    }
    if (type === 'label' && hostProps.text == null && children.length === 0) hostProps.text = ''
    return { props: Object.freeze(hostProps), children: children }
  }

  function expandSiblings(values, parentPath, eventHandlers, ids) {
    const result = []
    const keys = new Set()
    let index = 0
    function visit(value, path) {
      if (value == null || typeof value === 'boolean') return
      if (Array.isArray(value)) {
        for (const item of value) visit(item, path + '.' + index++)
        return
      }
      if (typeof value === 'string' || typeof value === 'number') {
        result.push(Object.freeze({ type: '#text', key: null, props: Object.freeze({ text: String(value) }), children: Object.freeze([]) }))
        return
      }
      if (!isVNode(value)) throw fail('render', 'Render values must be VNodes, arrays, text, or null')
      if (value.key != null) {
        if (keys.has(value.key)) throw fail('layout', 'Duplicate sibling key: ' + value.key)
        keys.add(value.key)
      }
      result.push(...expandNode(value, parentPath + '.' + index++, eventHandlers, ids))
    }
    for (const value of values) visit(value, parentPath)
    return result
  }

  function expandNode(node, path, eventHandlers, ids) {
    if (node.type === FRAGMENT) return expandSiblings(node.children, path, eventHandlers, ids)
    if (typeof node.type === 'function') {
      let result
      try {
        result = callGuest(node.type, [node.props])
      } catch (error) {
        throw fail('component', 'Component render failed', error)
      }
      const expanded = expandSiblings([result], path, eventHandlers, ids)
      if (node.key != null && expanded.length === 1 && expanded[0].key == null) {
        return [Object.freeze({ ...expanded[0], key: node.key })]
      }
      return expanded
    }
    if (typeof node.type !== 'string') throw fail('render', 'Invalid JSX type')
    validateProps(node.type, node.props)
    const rawTextChildren = TEXT_CHILDREN.has(node.type)
      && node.children.every(child => typeof child === 'string' || typeof child === 'number')
    let childNodes
    if (rawTextChildren) childNodes = node.children
    else childNodes = expandSiblings(node.children, path, eventHandlers, ids)
    const normalized = hostPropsFor(node.type, node.props, childNodes)
    const id = normalized.props.id
    if (id != null) {
      if (ids.has(id)) throw fail('layout', 'Duplicate UI id: ' + id)
      ids.add(id)
    }
    const callbacks = Object.create(null)
    for (const name of EVENT_PROPS) {
      if (typeof node.props[name] === 'function') callbacks[name] = node.props[name]
    }
    if (id != null && Object.keys(callbacks).length > 0) eventHandlers.set(id, Object.freeze(callbacks))
    const children = normalized.children
    return [Object.freeze({
      type: node.type,
      key: node.key,
      props: normalized.props,
      children: Object.freeze(children),
      path: path
    })]
  }

  function ownerThread(adapter) {
    return adapter.isOwnerThread()
  }

  function report(adapter, rootId, phase, error) {
    const diagnostic = Object.freeze({ rootId: rootId, phase: phase, error: error })
    try {
      adapter.reportDiagnostic(diagnostic)
    } catch (_) {
      // Diagnostics must not replace the UI failure.
    }
    return diagnostic
  }

  function cloneLayoutTree(nodes) {
    return Object.freeze(nodes.map(node => Object.freeze({
      type: node.type,
      key: node.key,
      props: node.props,
      children: cloneLayoutTree(node.children)
    })))
  }

  function removeTree(transaction, node) {
    for (const child of node.children) removeTree(transaction, child)
    transaction.remove(node.handle)
  }

  function reconcileChildren(transaction, previous, candidate, parent, state) {
    const oldByKey = new Map()
    for (const node of previous) if (node.key != null) oldByKey.set(node.key, node)
    const used = new Set()
    const next = []
    for (let i = 0; i < candidate.length; i++) {
      const node = candidate[i]
      let old = null
      if (node.key != null) {
        const keyed = oldByKey.get(node.key)
        if (keyed != null && keyed.type === node.type) old = keyed
      } else {
        const positional = previous[i]
        if (positional != null && positional.key == null && positional.type === node.type && !used.has(positional)) old = positional
      }
      let handle
      if (old != null) {
        used.add(old)
        handle = old.handle
        transaction.update(handle, node.type, node.key, node.props)
      } else {
        handle = transaction.create(node.type, node.key, node.props)
      }
      const children = reconcileChildren(transaction, old == null ? [] : old.children, node.children, handle, state)
      next.push(Object.freeze({ type: node.type, key: node.key, props: node.props, children: children, handle: handle }))
      transaction.order(handle, children.map(child => child.handle))
    }
    for (const old of previous) if (!used.has(old) && !next.some(node => node.handle === old.handle)) removeTree(transaction, old)
    transaction.order(parent, next.map(node => node.handle))
    return Object.freeze(next)
  }

  function scheduleRoot(root) {
    if (root.disposed || root.pending || !root.dirty) return
    root.pending = true
    const run = function () {
      root.pending = false
      if (root.disposed || !root.dirty) return
      root.dirty = false
      root.reconcile(false)
    }
    if (ownerThread(root.adapter)) {
      run()
      return
    }
    if (!root.adapter.enqueue(run)) {
      root.pending = false
      root.dirty = false
      throw new Error('UI update rejected off the owner thread')
    }
  }

  function flushRoots() {
    if (batchDepth > 0 || pendingRoots.size === 0) return
    const roots = Array.from(pendingRoots)
    pendingRoots.clear()
    for (const root of roots) scheduleRoot(root)
  }

  function markDirty(root) {
    if (root.disposed) return
    root.dirty = true
    pendingRoots.add(root)
    flushRoots()
  }

  function createSignal(initialValue: unknown): NekoUiSignal<unknown> {
    let value = freezeValue(initialValue, new WeakSet())
    const signal = {
      get: function () {
        if (activeTracker != null) activeTracker.dependencies.add(signal)
        return value
      },
      set: function (nextValue) {
        const next = freezeValue(nextValue, new WeakSet())
        const observers = signalObservers.get(signal)
        const liveRoots = Array.from(observers).filter(root => !root.disposed)
        const offThread = liveRoots.find(root => !ownerThread(root.adapter))
        if (offThread != null) {
          if (!offThread.adapter.enqueue(() => signal.set(next))) throw new Error('Signal write rejected off the owner thread')
          return 'queued'
        }
        if (Object.is(value, next)) return false
        value = next
        for (const root of liveRoots) markDirty(root)
        return true
      },
      update: function (updater) {
        if (typeof updater !== 'function') throw new TypeError('Signal updater must be a function')
        return signal.set(updater(value))
      }
    }
    signalObservers.set(signal, new Set())
    return Object.freeze(signal)
  }

  function createStore(initialValue: Record<string, unknown>): NekoUiStore {
    if (!plainObject(initialValue)) throw new TypeError('Store initial value must be a plain object')
    const cells = Object.create(null)
    for (const key of Object.keys(initialValue)) cells[key] = createSignal(initialValue[key])
    function cell(key) {
      if (typeof key !== 'string' || key.length === 0) throw new TypeError('Store key must be a non-empty string')
      if (!Object.prototype.hasOwnProperty.call(cells, key)) cells[key] = createSignal(undefined)
      return cells[key]
    }
    return Object.freeze({
      get: key => cell(key).get(),
      set: (key, value) => cell(key).set(value),
      update: (key, updater) => cell(key).update(updater),
      snapshot: function () {
        const snapshot = Object.create(null)
        for (const key of Object.keys(cells)) snapshot[key] = cells[key].get()
        return Object.freeze(snapshot)
      }
    })
  }

  function batch(callback: NekoUiCallback): unknown {
    if (typeof callback !== 'function') throw new TypeError('batch expects a function')
    batchDepth++
    try {
      return callback()
    } finally {
      batchDepth--
      flushRoots()
    }
  }

  function createRoot(render: NekoUiCallback, adapter: NekoUiHostAdapter, options?: NekoUiRootOptions): NekoUiRootHandle {
    if (typeof render !== 'function') throw new TypeError('UI root render must be a function')
    if (adapter == null || typeof adapter.isOwnerThread !== 'function'
      || typeof adapter.enqueue !== 'function' || typeof adapter.supportsPrimitive !== 'function'
      || typeof adapter.layout !== 'function' || typeof adapter.begin !== 'function'
      || typeof adapter.reportDiagnostic !== 'function') {
      throw new TypeError('UI host Adapter does not implement the common contract')
    }
    if (!ownerThread(adapter)) throw new Error('UI root must be created on the owner thread')

    const rootId = options != null && typeof options.id === 'string' ? options.id : 'ui-root-' + nextRootId++
    const root = {
      adapter: adapter,
      tree: Object.freeze([]),
      dependencies: new Set(),
      eventHandlers: new Map(),
      disposed: false,
      dirty: false,
      pending: false,
      reconcile: null
    }

    root.reconcile = function (initial) {
      if (root.disposed) throw new Error('UI root is disposed: ' + rootId)
      let candidate
      let tracker = { root: root, dependencies: new Set() }
      const previousTracker = activeTracker
      activeTracker = tracker
      try {
        let output
        try {
          output = render()
        } catch (error) {
          throw fail('render', 'UI render failed', error)
        }
        const eventHandlers = new Map()
        candidate = expandSiblings([output], rootId, eventHandlers, new Set())
        candidate = Object.freeze(candidate)
        for (const node of candidate) {
          const check = list => {
            for (const item of list) {
              if (item.type !== '#text' && !adapter.supportsPrimitive(item.type)) {
                throw fail('host-update', 'Host does not support primitive: ' + item.type)
              }
              check(item.children)
            }
          }
          check([node])
        }
        try {
          adapter.layout(cloneLayoutTree(candidate))
        } catch (error) {
          throw fail('layout', 'UI layout failed', error)
        }
        const transaction = adapter.begin()
        try {
          const nextTree = reconcileChildren(transaction, root.tree, candidate, null, root)
          transaction.commit(nextTree.map(node => node.handle))
          for (const signal of root.dependencies) if (!tracker.dependencies.has(signal)) signalObservers.get(signal).delete(root)
          for (const signal of tracker.dependencies) if (!root.dependencies.has(signal)) signalObservers.get(signal).add(root)
          root.dependencies = tracker.dependencies
          root.tree = nextTree
          root.eventHandlers = eventHandlers
          return true
        } catch (error) {
          try { transaction.rollback() } catch (rollbackError) {
            if (error != null && typeof error === 'object') error.rollbackError = rollbackError
          }
          throw fail('host-update', 'UI host update failed', error)
        }
      } catch (error) {
        const phase = error != null && error.uiPhase ? error.uiPhase : 'render'
        report(adapter, rootId, phase, error)
        if (initial) throw error
        return false
      } finally {
        activeTracker = previousTracker
      }
    }

    const handle = Object.freeze({
      id: rootId,
      refresh: function () {
        if (root.disposed) throw new Error('UI root is disposed: ' + rootId)
        if (!ownerThread(adapter)) {
          if (!adapter.enqueue(() => root.reconcile(false))) throw new Error('UI refresh rejected off the owner thread')
          return 'queued'
        }
        return root.reconcile(false)
      },
      dispatch: function (id, eventName, input) {
        if (root.disposed) return false
        const callbackName = EVENT_PROPS.find(name => name.slice(2).toLowerCase() === String(eventName).toLowerCase())
        if (callbackName == null) throw new TypeError('Unknown UI event: ' + eventName)
        const callbacks = root.eventHandlers.get(id)
        const callback = callbacks == null ? null : callbacks[callbackName]
        if (typeof callback !== 'function') return false
        if (!ownerThread(adapter)) {
          if (!adapter.enqueue(() => handle.dispatch(id, eventName, input))) throw new Error('UI event rejected off the owner thread')
          return 'queued'
        }
        const event = Object.freeze({
          type: callbackName.slice(2).toLowerCase(),
          target: id,
          x: input == null ? undefined : input.x,
          y: input == null ? undefined : input.y,
          button: input == null ? undefined : input.button,
          key: input == null ? undefined : input.key,
          value: input == null ? undefined : input.value,
          delta: input == null ? undefined : input.delta
        })
        try {
          callGuest(callback, [event])
          return true
        } catch (error) {
          report(adapter, rootId, 'event', error)
          return false
        }
      },
      isDisposed: function () { return root.disposed },
      close: function () {
        if (root.disposed) return false
        if (!ownerThread(adapter)) {
          if (!adapter.enqueue(() => handle.close())) throw new Error('UI close rejected off the owner thread')
          return 'queued'
        }
        for (const signal of root.dependencies) signalObservers.get(signal).delete(root)
        root.dependencies.clear()
        root.eventHandlers.clear()
        try {
          if (root.tree.length > 0) {
            const transaction = adapter.begin()
            try {
              for (const node of root.tree) removeTree(transaction, node)
              transaction.order(null, [])
              transaction.commit([])
            } catch (error) {
              try { transaction.rollback() } catch (_) {}
              report(adapter, rootId, 'host-update', error)
            }
          }
        } finally {
          root.tree = Object.freeze([])
          root.disposed = true
          root.dirty = false
          root.pending = false
        }
        return true
      }
    })
    root.reconcile(true)
    return handle
  }

  const UI: NekoUiApi = Object.freeze({
    primitives: function () { return Object.freeze(Object.keys(PRIMITIVES)) },
    element: createElement,
    createSignal: createSignal,
    createStore: createStore,
    createRoot: createRoot,
    batch: batch,
    fragment: FRAGMENT
  })

  globalThis.__nekoNodeDefine(['nekojs/jsx-runtime'], {
    jsx: (type: NekoUiJsxType, props: NekoUiPrimitiveProps | null, key?: string | number): NekoVNode => jsx(type, props, key),
    jsxs: (type: NekoUiJsxType, props: NekoUiPrimitiveProps | null, key?: string | number): NekoVNode => jsxs(type, props, key),
    Fragment: FRAGMENT,
    UI: UI
  })
})()
