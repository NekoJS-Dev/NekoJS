;(function () {
  type NekoUiProfile = 1 | 2 | 3 | 4 | 5 | 6
  type NekoUiPrimitive = 'screen' | 'panel' | 'row' | 'column' | 'stack' | 'scroll' | 'label' | 'button' | 'input' | 'image' | 'spacer'
  type NekoUiJsxType = NekoUiPrimitive | NekoUiComponent | symbol
  type NekoUiLayoutSize = number | 'auto' | 'fill' | `${number}%`
  type NekoUiResponsive<T> = T | { readonly base?: T; readonly profiles?: Partial<Record<NekoUiProfile, T>> }
  type NekoUiEventName = 'onClick' | 'onRelease' | 'onScroll' | 'onKey' | 'onTextInput' | 'onFocus' | 'onBlur' | 'onChange' | 'onSubmit'
  type NekoUiDirection = 'row' | 'column'
  type NekoUiAnchor = 'topLeft' | 'top' | 'topRight' | 'left' | 'center' | 'right' | 'bottomLeft' | 'bottom' | 'bottomRight'

  interface NekoUiSafeArea {
    readonly left?: number
    readonly top?: number
    readonly right?: number
    readonly bottom?: number
  }

  interface NekoUiViewportCapabilities {
    readonly maxProfile?: NekoUiProfile
  }

  interface NekoUiViewportInput {
    readonly width: number
    readonly height: number
    readonly safeArea?: NekoUiSafeArea
    readonly guiScale?: number
    readonly capabilities?: NekoUiViewportCapabilities
    readonly designWidth?: number
    readonly designHeight?: number
  }

  interface NekoUiViewport {
    readonly width: number
    readonly height: number
    readonly safeArea: Required<NekoUiSafeArea>
    readonly contentWidth: number
    readonly contentHeight: number
    readonly profile: NekoUiProfile
    readonly guiScale?: number
    readonly capabilities: NekoUiViewportCapabilities
    readonly designScale: number
  }

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

  interface NekoUiRect {
    readonly x: number
    readonly y: number
    readonly width: number
    readonly height: number
  }

  interface NekoUiLayoutNode {
    readonly type: NekoUiPrimitive | '#text'
    readonly key: string | null
    readonly id?: string
    readonly profile: NekoUiProfile
    readonly visible: boolean
    readonly rect: NekoUiRect
    readonly clip: NekoUiRect
    readonly overflow: Readonly<Record<'left' | 'top' | 'right' | 'bottom', number>>
    readonly style: Readonly<Record<string, unknown>>
    readonly children: readonly NekoUiLayoutNode[]
  }

  interface NekoUiLayoutSnapshot {
    readonly profile: NekoUiProfile
    readonly viewport: NekoUiViewport
    readonly nodes: readonly NekoUiLayoutNode[]
    readonly diagnostics: readonly string[]
  }

  interface NekoUiHostAdapter {
    isOwnerThread(): boolean
    enqueue(action: NekoUiCallback): boolean
    supportsPrimitive(type: NekoUiPrimitive): boolean
    layout(tree: readonly NekoUiHostNode[], viewport?: NekoUiViewport, snapshot?: NekoUiLayoutSnapshot): void
    measureText?(text: string, fontSize: number, maxWidth: number): { readonly width: number; readonly height: number }
    begin(): NekoUiHostTransaction
    reportDiagnostic(diagnostic: NekoUiDiagnostic): void
    viewport?(): NekoUiViewportInput
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
    resize(viewport: NekoUiViewportInput): boolean | 'queued'
    layout(): NekoUiLayoutSnapshot | null
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
    scrollX?: NekoUiResponsive<boolean>
    scrollY?: NekoUiResponsive<boolean>
    scrollOffset?: NekoUiResponsive<number>
    text?: string
    color?: string | number
    fontSize?: NekoUiResponsive<number>
    wrap?: boolean
    disabled?: boolean
    tooltip?: string
    value?: string
    placeholder?: string
    maxLength?: number
    resource?: string
    fit?: 'contain' | 'cover' | 'stretch'
    width?: NekoUiResponsive<NekoUiLayoutSize>
    height?: NekoUiResponsive<NekoUiLayoutSize>
    minWidth?: NekoUiResponsive<NekoUiLayoutSize>
    maxWidth?: NekoUiResponsive<NekoUiLayoutSize>
    minHeight?: NekoUiResponsive<NekoUiLayoutSize>
    maxHeight?: NekoUiResponsive<NekoUiLayoutSize>
    gap?: NekoUiResponsive<number>
    spacing?: NekoUiResponsive<number>
    padding?: NekoUiResponsive<number>
    align?: NekoUiResponsive<'start' | 'center' | 'end' | 'stretch'>
    justify?: NekoUiResponsive<'start' | 'center' | 'end' | 'spaceBetween' | 'spaceAround'>
    direction?: NekoUiResponsive<NekoUiDirection>
    anchor?: NekoUiResponsive<NekoUiAnchor>
    coordinateSpace?: 'logical' | 'design'
    visible?: NekoUiResponsive<boolean>
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
    viewport?: NekoUiViewportInput
  }

  interface NekoUiApi {
    primitives(): readonly NekoUiPrimitive[]
    resolveViewport(input: NekoUiViewportInput): NekoUiViewport
    profileFor(input: NekoUiViewportInput): NekoUiProfile
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
    'gap', 'spacing', 'padding', 'align', 'justify', 'direction', 'anchor',
    'coordinateSpace', 'visible'
  ])
  const EVENT_PROPS = Object.freeze([
    'onClick', 'onRelease', 'onScroll', 'onKey', 'onTextInput',
    'onFocus', 'onBlur', 'onChange', 'onSubmit'
  ])
  const DIMENSIONS = new Set(['width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight'])
  const ALIGNMENTS = new Set(['start', 'center', 'end', 'stretch'])
  const JUSTIFICATIONS = new Set(['start', 'center', 'end', 'spaceBetween', 'spaceAround'])
  const TEXT_CHILDREN = new Set(['label', 'button'])
  const PROFILE_WIDTHS = Object.freeze([0, 0, 320, 480, 640, 854, 1280])
  const PROFILE_HEIGHTS = Object.freeze([0, 0, 180, 240, 360, 480, 720])
  const ANCHORS = new Set(['topLeft', 'top', 'topRight', 'left', 'center', 'right', 'bottomLeft', 'bottom', 'bottomRight'])
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

  function responsiveValues(value) {
    if (!plainObject(value) || (!Object.prototype.hasOwnProperty.call(value, 'base')
      && !Object.prototype.hasOwnProperty.call(value, 'profiles'))) return [value]
    const values = []
    if (Object.prototype.hasOwnProperty.call(value, 'base')) values.push(value.base)
    const profiles = value.profiles
    if (profiles != null) {
      if (!plainObject(profiles)) throw fail('layout', 'profiles must be a plain object')
      for (const key of Object.keys(profiles)) {
        const profile = Number(key)
        if (!Number.isInteger(profile) || profile < 1 || profile > 6) {
          throw fail('layout', 'Invalid viewport profile: ' + key)
        }
        values.push(profiles[key])
      }
    }
    return values
  }

  function validateLayoutValue(value, name) {
    if (value === undefined || value === null || value === 'auto' || value === 'fill') return
    if (typeof value === 'string' && /^\d+(?:\.\d+)?%$/.test(value)) return
    finiteNonNegative(value, name)
  }

  function validateLayout(props) {
    for (const name of DIMENSIONS) for (const value of responsiveValues(props[name])) validateLayoutValue(value, name)
    for (const name of ['gap', 'spacing', 'padding', 'fontSize', 'scrollOffset']) {
      for (const value of responsiveValues(props[name])) if (value !== undefined && value !== null) finiteNonNegative(value, name)
    }
    for (const name of ['minWidth', 'maxWidth', 'minHeight', 'maxHeight']) {
      for (const value of responsiveValues(props[name])) validateLayoutValue(value, name)
    }
    for (const value of responsiveValues(props.align)) if (value != null && !ALIGNMENTS.has(value)) throw fail('layout', 'Invalid align value: ' + value)
    for (const value of responsiveValues(props.justify)) if (value != null && !JUSTIFICATIONS.has(value)) throw fail('layout', 'Invalid justify value: ' + value)
    for (const value of responsiveValues(props.direction)) if (value != null && (value !== 'row' && value !== 'column')) throw fail('layout', 'Invalid direction value: ' + value)
    for (const value of responsiveValues(props.anchor)) if (value != null && !ANCHORS.has(value)) throw fail('layout', 'Invalid anchor value: ' + value)
    for (const value of responsiveValues(props.visible)) if (value != null && typeof value !== 'boolean') throw fail('layout', 'visible must be a boolean')
    if (props.coordinateSpace != null && props.coordinateSpace !== 'logical' && props.coordinateSpace !== 'design') {
      throw fail('layout', 'coordinateSpace must be logical or design')
    }
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

  function profileTier(value, thresholds) {
    let profile = 1
    for (let candidate = 2; candidate <= 6; candidate++) {
      if (value >= thresholds[candidate]) profile = candidate
    }
    return profile
  }

  function resolveViewport(input) {
    if (!plainObject(input)) throw new TypeError('Viewport input must be a plain object')
    finitePositive(input.width, 'viewport.width')
    finitePositive(input.height, 'viewport.height')
    const source = input.safeArea == null ? {} : input.safeArea
    if (!plainObject(source)) throw new TypeError('Viewport safeArea must be a plain object')
    const safeArea = Object.freeze({
      left: source.left === undefined ? 0 : source.left,
      top: source.top === undefined ? 0 : source.top,
      right: source.right === undefined ? 0 : source.right,
      bottom: source.bottom === undefined ? 0 : source.bottom
    })
    for (const name of ['left', 'top', 'right', 'bottom']) finiteNonNegative(safeArea[name], 'safeArea.' + name)
    if (safeArea.left + safeArea.right >= input.width || safeArea.top + safeArea.bottom >= input.height) {
      throw new RangeError('Viewport safeArea must leave a positive content area')
    }
    if (input.guiScale !== undefined) finitePositive(input.guiScale, 'viewport.guiScale')
    const capabilities = input.capabilities == null ? {} : input.capabilities
    if (!plainObject(capabilities)) throw new TypeError('Viewport capabilities must be a plain object')
    const maxProfile = capabilities.maxProfile
    if (maxProfile !== undefined && (!Number.isInteger(maxProfile) || maxProfile < 1 || maxProfile > 6)) {
      throw new RangeError('Viewport capabilities.maxProfile must be an integer from 1 to 6')
    }
    const contentWidth = input.width - safeArea.left - safeArea.right
    const contentHeight = input.height - safeArea.top - safeArea.bottom
    const measuredProfile = Math.min(profileTier(contentWidth, PROFILE_WIDTHS), profileTier(contentHeight, PROFILE_HEIGHTS))
    const profile = Math.min(measuredProfile, maxProfile === undefined ? 6 : maxProfile)
    const hasDesignWidth = input.designWidth !== undefined
    const hasDesignHeight = input.designHeight !== undefined
    if (hasDesignWidth !== hasDesignHeight) throw new RangeError('Viewport designWidth and designHeight must be provided together')
    if (hasDesignWidth) {
      finitePositive(input.designWidth, 'viewport.designWidth')
      finitePositive(input.designHeight, 'viewport.designHeight')
    }
    const designScale = hasDesignWidth
      ? Math.min(contentWidth / input.designWidth, contentHeight / input.designHeight)
      : 1
    return Object.freeze({
      width: input.width,
      height: input.height,
      safeArea: safeArea,
      contentWidth: contentWidth,
      contentHeight: contentHeight,
      profile: profile,
      guiScale: input.guiScale,
      capabilities: Object.freeze({ maxProfile: maxProfile }),
      designScale: designScale
    })
  }

  function finitePositive(value, name) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
      throw new RangeError(name + ' must be a finite positive number')
    }
  }

  function resolveResponsiveValue(value, profile, fallback) {
    if (!plainObject(value) || (!Object.prototype.hasOwnProperty.call(value, 'base')
      && !Object.prototype.hasOwnProperty.call(value, 'profiles'))) return value === undefined ? fallback : value
    const profiles = value.profiles
    if (profiles != null && !plainObject(profiles)) throw fail('layout', 'profiles must be a plain object')
    if (profiles != null) {
      for (let candidate = profile; candidate >= 1; candidate--) {
        if (Object.prototype.hasOwnProperty.call(profiles, String(candidate))) return profiles[String(candidate)]
      }
    }
    return Object.prototype.hasOwnProperty.call(value, 'base') ? value.base : fallback
  }

  function resolvedProps(props, viewport) {
    const resolved = Object.create(null)
    for (const name of Object.keys(props)) {
      if (name === 'children' || EVENT_PROPS.includes(name)) continue
      resolved[name] = resolveResponsiveValue(props[name], viewport.profile, undefined)
    }
    return resolved
  }

  function dimension(value, available, intrinsic, scale) {
    if (value === undefined || value === null || value === 'auto') return intrinsic
    if (value === 'fill') return available
    if (typeof value === 'string' && /^\d+(?:\.\d+)?%$/.test(value)) return available * Number(value.slice(0, -1)) / 100
    return value * scale
  }

  function clampDimension(value, minValue, maxValue, available, scale, name) {
    const min = minValue === undefined || minValue === null ? 0 : dimension(minValue, available, 0, scale)
    const max = maxValue === undefined || maxValue === null ? Infinity : dimension(maxValue, available, Infinity, scale)
    if (min > max) throw fail('layout', name + ' min must not exceed max')
    return Math.min(Math.max(value, min), max)
  }

  function textMetrics(adapter, text, fontSize, maxWidth) {
    if (typeof adapter.measureText === 'function') {
      const result = adapter.measureText(text, fontSize, maxWidth)
      if (result == null || !Number.isFinite(result.width) || !Number.isFinite(result.height)
        || result.width < 0 || result.height < 0) throw fail('layout', 'Font Adapter returned invalid text metrics')
      return result
    }
    return { width: Math.min(maxWidth, text.length * fontSize * 0.5), height: fontSize }
  }

  function intrinsicSize(node, availableWidth, availableHeight, viewport, adapter) {
    const props = resolvedProps(node.props, viewport)
    if (props.visible === false) return { width: 0, height: 0 }
    const scale = props.coordinateSpace === 'design' ? viewport.designScale : 1
    if (node.type === '#text' || TEXT_CHILDREN.has(node.type)) {
      const text = String(props.text == null ? '' : props.text)
      const fontSize = props.fontSize == null ? 9 * scale : props.fontSize * scale
      const measured = textMetrics(adapter, text, fontSize, Math.max(0, availableWidth))
      return { width: measured.width, height: measured.height }
    }
    const children = node.children
    if (children.length === 0) return { width: 0, height: 0 }
    const direction = props.direction || (node.type === 'row' ? 'row' : node.type === 'column' ? 'column' : 'column')
    const gap = Number(props.spacing == null ? (props.gap == null ? 0 : props.gap) : props.spacing) * scale
    const padding = Number(props.padding || 0) * scale
    const sizes = children.map(child => intrinsicSize(child, availableWidth, availableHeight, viewport, adapter))
    if (node.type === 'stack') {
      return { width: Math.max(...sizes.map(size => size.width), 0) + padding * 2, height: Math.max(...sizes.map(size => size.height), 0) + padding * 2 }
    }
    if (direction === 'row') {
      return { width: sizes.reduce((total, size) => total + size.width, 0) + Math.max(0, sizes.length - 1) * gap + padding * 2, height: Math.max(...sizes.map(size => size.height), 0) + padding * 2 }
    }
    return { width: Math.max(...sizes.map(size => size.width), 0) + padding * 2, height: sizes.reduce((total, size) => total + size.height, 0) + Math.max(0, sizes.length - 1) * gap + padding * 2 }
  }

  function intersect(a, b) {
    const left = Math.max(a.x, b.x)
    const top = Math.max(a.y, b.y)
    const right = Math.min(a.x + a.width, b.x + b.width)
    const bottom = Math.min(a.y + a.height, b.y + b.height)
    return Object.freeze({ x: left, y: top, width: Math.max(0, right - left), height: Math.max(0, bottom - top) })
  }

  function anchorOffset(anchor, width, height, childWidth, childHeight) {
    let x = (width - childWidth) / 2
    let y = (height - childHeight) / 2
    if (anchor.endsWith('Right') || anchor === 'right') x = width - childWidth
    else if (anchor.endsWith('Left') || anchor === 'left') x = 0
    if (anchor.startsWith('bottom') || anchor === 'bottom') y = height - childHeight
    else if (anchor.startsWith('top') || anchor === 'top') y = 0
    return { x: x, y: y }
  }

  function layoutNode(node, x, y, availableWidth, availableHeight, parentClip, viewport, adapter, diagnostics, rootNode, allocated) {
    const props = resolvedProps(node.props, viewport)
    const scale = props.coordinateSpace === 'design' ? viewport.designScale : 1
    const intrinsic = intrinsicSize(node, availableWidth, availableHeight, viewport, adapter)
    let width = allocated || props.width === undefined ? availableWidth : dimension(props.width, availableWidth, intrinsic.width, scale)
    let height = allocated || props.height === undefined ? availableHeight : dimension(props.height, availableHeight, intrinsic.height, scale)
    width = clampDimension(width, props.minWidth, props.maxWidth, availableWidth, scale, 'width')
    height = clampDimension(height, props.minHeight, props.maxHeight, availableHeight, scale, 'height')
    const visible = props.visible !== false
    if (!visible) { width = 0; height = 0 }
    const rect = Object.freeze({ x: x, y: y, width: width, height: height })
    const clip = intersect(rect, parentClip)
    const overflow = Object.freeze({
      left: Math.max(parentClip.x - rect.x, 0),
      top: Math.max(parentClip.y - rect.y, 0),
      right: Math.max(rect.x + rect.width - parentClip.x - parentClip.width, 0),
      bottom: Math.max(rect.y + rect.height - parentClip.y - parentClip.height, 0)
    })
    const overflowNames = Object.keys(overflow).filter(name => overflow[name] > 0)
    const id = props.id == null ? node.type : props.id
    for (const name of overflowNames) diagnostics.push(id + ':overflow-' + name)
    const children = []
    const padding = Number(props.padding || 0) * scale
    const innerX = x + padding
    const innerY = y + padding
    const innerWidth = Math.max(0, width - padding * 2)
    const innerHeight = Math.max(0, height - padding * 2)
    const layoutClip = clip
    if (visible && node.children.length > 0 && node.type !== '#text' && node.type !== 'stack' && !TEXT_CHILDREN.has(node.type)) {
      const direction = props.direction || (node.type === 'row' ? 'row' : node.type === 'column' ? 'column' : 'column')
      const gap = Number(props.spacing == null ? (props.gap == null ? 0 : props.gap) : props.spacing) * scale
      const mainSize = direction === 'row' ? innerWidth : innerHeight
      const crossSize = direction === 'row' ? innerHeight : innerWidth
      const childRecords = node.children.map(child => {
        const childProps = resolvedProps(child.props, viewport)
        const childIntrinsic = intrinsicSize(child, innerWidth, innerHeight, viewport, adapter)
        const rawMain = direction === 'row' ? childProps.width : childProps.height
        const rawCross = direction === 'row' ? childProps.height : childProps.width
        return { child: child, props: childProps, intrinsic: childIntrinsic, rawMain: rawMain, rawCross: rawCross, main: 0, cross: 0 }
      })
      const fillCount = childRecords.filter(record => record.rawMain === 'fill').length
      let fixedMain = Math.max(0, childRecords.length - 1) * gap
      for (const record of childRecords) {
        if (record.props.visible === false) continue
        if (record.rawMain !== 'fill') record.main = dimension(record.rawMain, mainSize, direction === 'row' ? record.intrinsic.width : record.intrinsic.height, record.props.coordinateSpace === 'design' ? viewport.designScale : 1)
        fixedMain += record.main
      }
      const fillMain = fillCount === 0 ? 0 : Math.max(0, mainSize - fixedMain) / fillCount
      let usedMain = Math.max(0, childRecords.length - 1) * gap
      for (const record of childRecords) {
        if (record.props.visible !== false) record.main = record.rawMain === 'fill' ? fillMain : record.main
        const intrinsicCross = direction === 'row' ? record.intrinsic.height : record.intrinsic.width
        record.cross = dimension(record.rawCross, crossSize, intrinsicCross, record.props.coordinateSpace === 'design' ? viewport.designScale : 1)
        const align = record.props.align || props.align || 'start'
        if (align === 'stretch' && (record.rawCross === undefined || record.rawCross === 'auto' || record.rawCross === 'fill')) record.cross = crossSize
        usedMain += record.props.visible === false ? 0 : record.main
      }
      const extra = mainSize - usedMain
      const justify = props.justify || 'start'
      let offset = justify === 'center' ? Math.max(0, extra) / 2 : justify === 'end' ? Math.max(0, extra) : 0
      let actualGap = gap
      if (justify === 'spaceBetween' && childRecords.length > 1) actualGap = gap + Math.max(0, extra) / (childRecords.length - 1)
      if (justify === 'spaceAround' && childRecords.length > 0) { actualGap = gap + Math.max(0, extra) / childRecords.length; offset = actualGap / 2 }
      for (const record of childRecords) {
        if (record.props.visible === false) {
          children.push(layoutNode(record.child, innerX, innerY, 0, 0, layoutClip, viewport, adapter, diagnostics, false, true))
          continue
        }
        const align = record.props.align || props.align || 'start'
        const crossOffset = align === 'center' ? Math.max(0, crossSize - record.cross) / 2 : align === 'end' ? Math.max(0, crossSize - record.cross) : 0
        const childX = direction === 'row' ? innerX + offset : innerX + crossOffset
        const childY = direction === 'row' ? innerY + crossOffset : innerY + offset
        const childWidth = direction === 'row' ? record.main : record.cross
        const childHeight = direction === 'row' ? record.cross : record.main
        const scrollOffset = node.type === 'scroll' ? Number(props.scrollOffset || 0) * scale : 0
        children.push(layoutNode(record.child, childX - (direction === 'row' ? scrollOffset : 0), childY - (direction === 'column' ? scrollOffset : 0), childWidth, childHeight, layoutClip, viewport, adapter, diagnostics, false, true))
        offset += record.main + actualGap
      }
    } else if (visible && node.type === 'stack') {
      for (const child of node.children) {
        const childIntrinsic = intrinsicSize(child, innerWidth, innerHeight, viewport, adapter)
        const childProps = resolvedProps(child.props, viewport)
        const childWidth = dimension(childProps.width, innerWidth, childIntrinsic.width, childProps.coordinateSpace === 'design' ? viewport.designScale : 1)
        const childHeight = dimension(childProps.height, innerHeight, childIntrinsic.height, childProps.coordinateSpace === 'design' ? viewport.designScale : 1)
        const anchor = childProps.anchor || props.anchor || 'topLeft'
        const position = anchorOffset(anchor, innerWidth, innerHeight, childWidth, childHeight)
        children.push(layoutNode(child, innerX + position.x, innerY + position.y, childWidth, childHeight, layoutClip, viewport, adapter, diagnostics, false, true))
      }
    }
    return Object.freeze({
      type: node.type,
      key: node.key,
      id: props.id,
      profile: viewport.profile,
      visible: visible,
      rect: rect,
      clip: clip,
      overflow: overflow,
      style: Object.freeze(props),
      children: Object.freeze(children)
    })
  }

  function layoutSnapshotFor(nodes, viewport, adapter) {
    const diagnostics = []
    const rootClip = Object.freeze({ x: viewport.safeArea.left, y: viewport.safeArea.top, width: viewport.contentWidth, height: viewport.contentHeight })
    const layoutNodes = nodes.map(node => layoutNode(node, rootClip.x, rootClip.y, rootClip.width, rootClip.height, rootClip, viewport, adapter, diagnostics, true, false))
    return Object.freeze({ profile: viewport.profile, viewport: viewport, nodes: Object.freeze(layoutNodes), diagnostics: Object.freeze(diagnostics) })
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

  function expandSiblings(values, parentPath, eventHandlers, ids, siblingKeys) {
    const result = []
    const keys = siblingKeys == null ? new Set() : siblingKeys
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
      result.push(...expandNode(value, parentPath + '.' + index++, eventHandlers, ids, keys))
    }
    for (const value of values) visit(value, parentPath)
    return result
  }

  function expandNode(node, path, eventHandlers, ids, siblingKeys) {
    if (node.type === FRAGMENT) return expandSiblings(node.children, path, eventHandlers, ids, siblingKeys)
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
        const observers = signalObservers.get(signal)
        const liveRoots = Array.from(observers).filter(root => !root.disposed)
        const offThread = liveRoots.find(root => !ownerThread(root.adapter))
        if (offThread != null) {
          if (!offThread.adapter.enqueue(() => signal.update(updater))) throw new Error('Signal update rejected off the owner thread')
          return 'queued'
        }
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
    let initialInput = { width: 320, height: 180 }
    if (typeof adapter.viewport === 'function') initialInput = adapter.viewport()
    if (options != null && options.viewport != null) initialInput = options.viewport
    const root = {
      adapter: adapter,
      tree: Object.freeze([]),
      candidate: Object.freeze([]),
      viewport: resolveViewport(initialInput),
      layoutSnapshot: null,
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
        let layoutSnapshot
        try {
          layoutSnapshot = layoutSnapshotFor(candidate, root.viewport, adapter)
          adapter.layout(cloneLayoutTree(candidate), root.viewport, layoutSnapshot)
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
          root.candidate = candidate
          root.layoutSnapshot = layoutSnapshot
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
      resize: function (input) {
        if (root.disposed) throw new Error('UI root is disposed: ' + rootId)
        if (!ownerThread(adapter)) {
          if (!adapter.enqueue(() => handle.resize(input))) throw new Error('UI resize rejected off the owner thread')
          return 'queued'
        }
        const nextViewport = resolveViewport(input)
        if (root.candidate.length === 0) {
          root.viewport = nextViewport
          return false
        }
        try {
          const nextSnapshot = layoutSnapshotFor(root.candidate, nextViewport, adapter)
          adapter.layout(cloneLayoutTree(root.candidate), nextViewport, nextSnapshot)
          root.viewport = nextViewport
          root.layoutSnapshot = nextSnapshot
          return true
        } catch (error) {
          report(adapter, rootId, 'layout', fail('layout', 'UI resize layout failed', error))
          return false
        }
      },
      layout: function () { return root.layoutSnapshot },
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
        let transaction = null
        try {
          if (root.tree.length > 0) {
            transaction = adapter.begin()
            for (const node of root.tree) removeTree(transaction, node)
            transaction.order(null, [])
            transaction.commit([])
          }
        } catch (error) {
          try { if (transaction != null) transaction.rollback() } catch (_) {}
          report(adapter, rootId, 'host-update', error)
          return false
        }
        for (const signal of root.dependencies) signalObservers.get(signal).delete(root)
        root.dependencies.clear()
        root.eventHandlers.clear()
        root.tree = Object.freeze([])
        root.candidate = Object.freeze([])
        root.layoutSnapshot = null
        root.disposed = true
        root.dirty = false
        root.pending = false
        return true
      }
    })
    root.reconcile(true)
    return handle
  }

  const UI: NekoUiApi = Object.freeze({
    primitives: function () { return Object.freeze(Object.keys(PRIMITIVES)) },
    resolveViewport: resolveViewport,
    profileFor: function (input) { return resolveViewport(input).profile },
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
