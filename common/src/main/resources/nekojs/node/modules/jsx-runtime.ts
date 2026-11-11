type NekoUiProfile = 1 | 2 | 3 | 4 | 5 | 6
  type NekoUiPrimitive = 'screen' | 'panel' | 'row' | 'column' | 'stack' | 'scroll' | 'label' | 'button' | 'input' | 'slider' | 'image' | 'spacer' | 'dialog' | 'item' | 'workspace' | 'window'
  // VNodes store heterogeneous components; factories infer each component's actual props.
  type NekoUiJsxType = NekoUiPrimitive | NekoUiComponent<never> | symbol
  type NekoUiLayoutSize = number | 'auto' | 'fill' | `${number}%`
  type NekoUiResponsive<T> = T | { readonly base?: T; readonly profiles?: Partial<Record<NekoUiProfile, T>> }
  type NekoUiEventName = 'onClick' | 'onRelease' | 'onScroll' | 'onKey' | 'onTextInput' | 'onFocus' | 'onBlur' | 'onChange' | 'onSubmit' | 'onDismiss' | 'onMove' | 'onActivate' | 'onResize' | 'onMinimize' | 'onMaximize' | 'onRestore'
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
    readonly width?: number
    readonly height?: number
    readonly edge?: NekoUiWindowResizeEdge
    readonly button?: number
    readonly key?: string
    readonly value?: string
    readonly reason?: 'escape' | 'backdrop' | 'close'
    readonly delta?: number
  }

  type NekoUiWindowResizeEdge = 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw'
  interface NekoUiWindowResizeEvent extends NekoUiEvent {
    readonly x: number
    readonly y: number
    readonly width: number
    readonly height: number
    readonly edge: NekoUiWindowResizeEdge
  }

  interface NekoVNode {
    readonly $$nekoJsx: true
    readonly type: string | symbol | NekoUiComponent<never>
    readonly key: string | null
    readonly props: Readonly<Record<string, unknown>>
    readonly children: readonly NekoUiRenderable[]
  }

  type NekoUiComponent<Props = Record<string, unknown>> = (props: Props) => NekoUiRenderable
  type NekoUiCallback = () => void
  type NekoUiRender = () => NekoUiRenderable
  type NekoUiUpdater<T> = (current: T) => T
  type NekoUiEventCallback = (event: NekoUiEvent) => void

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
    readonly bindings: readonly string[]
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
    layout(tree: readonly NekoUiHostNode[], viewport: NekoUiViewport, snapshot: NekoUiLayoutSnapshot, publish?: boolean): void
    measureText?(text: string, fontSize: number, maxWidth: number, font?: string): { readonly width: number; readonly height: number }
    begin(): NekoUiHostTransaction
    reportDiagnostic(diagnostic: NekoUiDiagnostic): void
    viewport?(): NekoUiViewportInput
  }

  interface NekoUiSignal<T> {
    get(): T
    set(value: T): boolean | 'queued'
    update(updater: NekoUiUpdater<T>): boolean | 'queued'
  }

  interface NekoUiStore<State extends Record<string, unknown> = Record<string, unknown>> {
    get<Key extends keyof State & string>(key: Key): State[Key]
    set<Key extends keyof State & string>(key: Key, value: State[Key]): boolean | 'queued'
    update<Key extends keyof State & string>(key: Key, updater: NekoUiUpdater<State[Key]>): boolean | 'queued'
    snapshot(): Readonly<State>
  }

  interface NekoUiRootHandle {
    readonly id: string
    refresh(): boolean | 'queued'
    resize(viewport: NekoUiViewportInput): boolean | 'queued'
    layout(): NekoUiLayoutSnapshot | null
    dispatch(id: string, eventName: string, event: Partial<NekoUiEvent>): boolean | 'queued'
    isDisposed(): boolean
    isUpdating(): boolean
    close(): boolean | 'queued'
    forceClose(): void
  }

  interface NekoUiSharedProps {
    id?: string
    key?: string | number
    children?: NekoUiRenderable
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
    onClick?: NekoUiEventCallback
    onRelease?: NekoUiEventCallback
    onScroll?: NekoUiEventCallback
    onKey?: NekoUiEventCallback
    onTextInput?: NekoUiEventCallback
    onFocus?: NekoUiEventCallback
    onBlur?: NekoUiEventCallback
    onChange?: NekoUiEventCallback
    onSubmit?: NekoUiEventCallback
    onDismiss?: NekoUiEventCallback
    onMove?: NekoUiEventCallback
    onActivate?: NekoUiEventCallback
  }
  type NekoUiColor = string | number
  interface NekoUiControlColors {
    background?: NekoUiColor
    borderColor?: NekoUiColor
    color?: NekoUiColor
    placeholderColor?: NekoUiColor
    selectionColor?: NekoUiColor
    cursorColor?: NekoUiColor
    trackColor?: NekoUiColor
    thumbColor?: NekoUiColor
  }
  interface NekoUiControlAppearance extends NekoUiControlColors {
    hover?: NekoUiControlColors
    focused?: NekoUiControlColors
    pressed?: NekoUiControlColors
    disabled?: NekoUiControlColors
  }
  type NekoUiControlProps = Omit<NekoUiSharedProps, NekoUiEventName | 'id' | 'children'> & {
    id: string
    disabled?: boolean
    tooltip?: string
    appearance?: NekoUiControlAppearance
  }
  type NekoUiToggleProps = NekoUiControlProps & {
    checked: boolean
    label?: string
    text?: string
    onChange: (checked: boolean) => void
  }
  type NekoUiSliderProps = NekoUiControlProps & {
    value: number
    min?: number
    max?: number
    step?: number
    onChange: (value: number) => void
  }
  interface NekoUiSelectOption {
    value: string
    label: string
    disabled?: boolean
  }
  type NekoUiSelectProps = NekoUiControlProps & {
    value: string
    options: readonly NekoUiSelectOption[]
    expanded: boolean
    onExpandedChange: (expanded: boolean) => void
    onChange: (value: string) => void
  }
  type NekoUiToggle = (props: NekoUiToggleProps) => NekoVNode
  type NekoUiSlider = (props: NekoUiSliderProps) => NekoVNode
  type NekoUiSelect = (props: NekoUiSelectProps) => NekoVNode
  type NekoUiRenderable = NekoVNode | string | number | boolean | null | undefined | readonly NekoUiRenderable[]
  interface NekoUiTab {
    value: string
    label: string
    disabled?: boolean
    render: () => NekoUiRenderable
  }
  type NekoUiTabsProps = NekoUiControlProps & {
    value: string
    tabs: readonly NekoUiTab[]
    onChange: (value: string) => void
  }
  type NekoUiTabs = (props: NekoUiTabsProps) => NekoVNode
  interface NekoUiDialogProps {
    id: string
    open: boolean
    onOpenChange: (open: boolean) => void
    title?: string
    children?: NekoUiRenderable
    width?: NekoUiResponsive<NekoUiLayoutSize>
    height?: NekoUiResponsive<NekoUiLayoutSize>
    closeOnEscape?: boolean
    closeOnBackdrop?: boolean
  }
  type NekoUiDialog = (props: NekoUiDialogProps) => NekoVNode | null
  type NekoUiFormValue = string | number | boolean | null
  type NekoUiFormValues = Readonly<Record<string, NekoUiFormValue>>
  type NekoUiValidator = (value: NekoUiFormValue | undefined, values: NekoUiFormValues) => string | null | undefined
  interface NekoUiValidationResult {
    readonly valid: boolean
    readonly errors: Readonly<Record<string, readonly string[]>>
  }
  type NekoUiValidateForm = (values: NekoUiFormValues, rules: Readonly<Record<string, readonly NekoUiValidator[]>>) => NekoUiValidationResult
  interface NekoUiValidators {
    required(message?: string): NekoUiValidator
    minLength(length: number, message?: string): NekoUiValidator
    maxLength(length: number, message?: string): NekoUiValidator
    numberRange(min: number, max: number, message?: string): NekoUiValidator
  }
  type NekoUiCrop = { readonly x: number; readonly y: number; readonly width: number; readonly height: number } | readonly [number, number, number, number]
  type NekoUiWindowResponsive<T> = T | { readonly base?: T; readonly profiles?: Readonly<Partial<Record<NekoUiProfile, T>>> }
  interface NekoUiWindowResponsiveRect {
    readonly x?: NekoUiWindowResponsive<number | `${number}%`>
    readonly y?: NekoUiWindowResponsive<number | `${number}%`>
    readonly width?: NekoUiWindowResponsive<number | `${number}%`>
    readonly height?: NekoUiWindowResponsive<number | `${number}%`>
  }
  type NekoUiPrimitivePropsByType = {
    screen: NekoUiSharedProps & { title?: string; pausesGame?: boolean; closeOnEscape?: boolean; layout?: 'stack' }
    panel: NekoUiSharedProps & { background?: string | number; borderColor?: string | number; borderWidth?: number; radius?: number; opacity?: number; workspacePresentation?: boolean }
    row: NekoUiSharedProps
    column: NekoUiSharedProps
    stack: NekoUiSharedProps
    workspace: NekoUiSharedProps
    window: Omit<NekoUiSharedProps, 'width' | 'height' | 'minWidth' | 'minHeight' | 'maxWidth' | 'maxHeight'> & { id: string; title: string; x: number; y: number; width: number; height: number; minWidth?: number; minHeight?: number; maxWidth?: number; maxHeight?: number; responsiveRect?: NekoUiWindowResponsiveRect; mode?: 'normal' | 'maximized'; active?: boolean; draggable?: boolean; closable?: boolean; titlebarHeight?: number; titlebarButtonWidth?: number; titlebarTitle?: boolean; resizable?: boolean; minimizable?: boolean; maximizable?: boolean; onResize?: (event: NekoUiWindowResizeEvent) => void; onMinimize?: NekoUiEventCallback; onMaximize?: NekoUiEventCallback; onRestore?: NekoUiEventCallback }
    scroll: NekoUiSharedProps & { scrollX?: NekoUiResponsive<boolean>; scrollY?: NekoUiResponsive<boolean>; scrollOffset?: NekoUiResponsive<number> }
    label: NekoUiSharedProps & { text?: string; color?: string | number; fontSize?: NekoUiResponsive<number>; font?: NekoUiResponsive<string>; wrap?: boolean; truncate?: boolean }
    button: NekoUiSharedProps & { text?: string; disabled?: boolean; tooltip?: string; appearance?: NekoUiControlAppearance }
    input: NekoUiSharedProps & { value?: string; placeholder?: string; maxLength?: number; disabled?: boolean; tooltip?: string; appearance?: NekoUiControlAppearance }
    slider: NekoUiSharedProps & { value?: number; min?: number; max?: number; step?: number; disabled?: boolean; tooltip?: string; appearance?: NekoUiControlAppearance }
    image: NekoUiSharedProps & { resource?: string; fit?: 'contain' | 'cover' | 'stretch'; opacity?: number; icon?: string; crop?: NekoUiCrop }
    spacer: NekoUiSharedProps
    dialog: Omit<NekoUiSharedProps, 'onDismiss'> & { id: string; title?: string; closeOnEscape?: boolean; closeOnBackdrop?: boolean; onDismiss?: (event: Omit<NekoUiEvent, 'reason'> & { readonly reason?: 'escape' | 'backdrop' }) => void }
    item: NekoUiSharedProps & { item: string; count?: number; itemTooltip?: boolean; tooltip?: string }
  }
  type NekoUiPrimitiveProps = NekoUiPrimitivePropsByType[NekoUiPrimitive]
  type NekoUiElementProps<T extends NekoUiJsxType> = T extends NekoUiPrimitive ? NekoUiPrimitivePropsByType[T] : T extends (props: infer P) => unknown ? P : object

  type NekoUiJsxFactory = <T extends NekoUiJsxType>(type: T, props: NekoUiElementProps<T> | null, key?: string | number) => NekoVNode

  interface NekoUiFragment {
    (props: object): NekoVNode
  }

  declare namespace JSX {
    type ElementType = NekoUiPrimitive | NekoUiComponent<never>
    interface Element extends NekoVNode {}
    interface ElementChildrenAttribute { children: {} }
    interface IntrinsicElements {
      screen: NekoUiPrimitivePropsByType['screen']
      panel: NekoUiPrimitivePropsByType['panel']
      row: NekoUiPrimitivePropsByType['row']
      column: NekoUiPrimitivePropsByType['column']
      stack: NekoUiPrimitivePropsByType['stack']
      workspace: NekoUiPrimitivePropsByType['workspace']
      window: NekoUiPrimitivePropsByType['window']
      scroll: NekoUiPrimitivePropsByType['scroll']
      label: NekoUiPrimitivePropsByType['label']
      button: NekoUiPrimitivePropsByType['button']
      input: NekoUiPrimitivePropsByType['input']
      slider: NekoUiPrimitivePropsByType['slider']
      image: NekoUiPrimitivePropsByType['image']
      spacer: NekoUiPrimitivePropsByType['spacer']
      dialog: NekoUiPrimitivePropsByType['dialog']
      item: NekoUiPrimitivePropsByType['item']
    }
  }

;(function () {

  interface NekoUiRootOptions {
    id?: string
    viewport?: NekoUiViewportInput
  }

  interface NekoUiApi {
    primitives(): readonly NekoUiPrimitive[]
    resolveViewport(input: NekoUiViewportInput): NekoUiViewport
    profileFor(input: NekoUiViewportInput): NekoUiProfile
    element<T extends NekoUiJsxType>(type: T, props: NekoUiElementProps<T> | null, key?: string | number): NekoVNode
    createSignal<T>(initial: T): NekoUiSignal<T>
    createStore<State extends Record<string, unknown>>(initial: State): NekoUiStore<State>
    createRoot(render: NekoUiRender, adapter: NekoUiHostAdapter, options?: NekoUiRootOptions): NekoUiRootHandle
    batch<T>(callback: () => T): T
    readonly fragment: NekoUiFragment
  }
  const FRAGMENT: NekoUiFragment = Symbol('nekojs.jsx.fragment') as unknown as NekoUiFragment
  const PRIMITIVES = Object.freeze({
    screen: ['title', 'pausesGame', 'closeOnEscape', 'layout'],
    panel: ['background', 'borderColor', 'borderWidth', 'radius', 'opacity', 'workspacePresentation'],
    row: [],
    column: [],
    stack: [],
    workspace: [],
    window: ['title', 'x', 'y', 'active', 'draggable', 'closable', 'titlebarHeight', 'titlebarButtonWidth', 'titlebarTitle', 'responsiveRect', 'mode', 'resizable', 'minimizable', 'maximizable'],
    scroll: ['scrollX', 'scrollY', 'scrollOffset'],
    label: ['text', 'color', 'fontSize', 'font', 'wrap', 'truncate'],
    button: ['text', 'disabled', 'tooltip', 'appearance'],
    input: ['value', 'placeholder', 'maxLength', 'disabled', 'tooltip', 'appearance'],
    slider: ['value', 'min', 'max', 'step', 'disabled', 'tooltip', 'appearance'],
    image: ['resource', 'fit', 'opacity', 'icon', 'crop'],
    spacer: [],
    dialog: ['title', 'closeOnEscape', 'closeOnBackdrop', 'onDismiss'],
    item: ['item', 'count', 'itemTooltip', 'tooltip']
  })
  const SHARED_PROPS = Object.freeze([
    'id', 'width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight',
    'gap', 'spacing', 'padding', 'align', 'justify', 'direction', 'anchor',
    'coordinateSpace', 'visible'
  ])
  const EVENT_PROPS = Object.freeze([
    'onClick', 'onRelease', 'onScroll', 'onKey', 'onTextInput',
    'onFocus', 'onBlur', 'onChange', 'onSubmit', 'onDismiss', 'onMove', 'onActivate',
    'onResize', 'onMinimize', 'onMaximize', 'onRestore'
  ])
  const WINDOW_EVENT_PROPS = new Set(['onResize', 'onMinimize', 'onMaximize', 'onRestore'])
  const WINDOW_RESIZE_EDGES = new Set(['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'])
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
  const componentTypes = new WeakMap()
  let nextComponentType = 1

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

  function jsx<T extends NekoUiJsxType>(type: T, props: NekoUiElementProps<T> | null, key?: string | number): NekoVNode {
    return createElement(type, props, key)
  }

  function jsxs<T extends NekoUiJsxType>(type: T, props: NekoUiElementProps<T> | null, key?: string | number): NekoVNode {
    return createElement(type, props, key)
  }

  // Matches the Java VisualStyleResolver crop contract: {x, y, width, height} or a
  // 4-number array; the resolver still owns the semantic checks (non-negative origin,
  // positive size).
  function validCrop(value) {
    if (Array.isArray(value)) {
      return value.length === 4 && value.every(part => Number.isInteger(part))
    }
    if (plainObject(value)) {
      return ['x', 'y', 'width', 'height'].every(key => Number.isInteger(value[key]))
    }
    return false
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
    for (const value of responsiveValues(props.font)) if (value != null && typeof value !== 'string') throw fail('layout', 'font must be a controlled resource id string')
    if (props.coordinateSpace != null && props.coordinateSpace !== 'logical' && props.coordinateSpace !== 'design') {
      throw fail('layout', 'coordinateSpace must be logical or design')
    }
  }

  const CONTROL_COLORS = new Set(['background', 'borderColor', 'color', 'placeholderColor', 'selectionColor', 'cursorColor', 'trackColor', 'thumbColor'])
  const CONTROL_STATES = new Set(['hover', 'focused', 'pressed', 'disabled'])
  const NAMED_COLORS = new Set(['transparent', 'black', 'silver', 'gray', 'white', 'maroon', 'red', 'purple', 'fuchsia', 'green', 'lime', 'olive', 'yellow', 'navy', 'blue', 'teal', 'aqua'])

  function validControlColor(value) {
    if (typeof value === 'number') return Number.isInteger(value) && value >= -2147483648 && value <= 4294967295
    if (typeof value !== 'string') return false
    const text = value.trim().toLowerCase()
    return NAMED_COLORS.has(text) || /^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/.test(text)
  }

  function validateAppearance(value, states) {
    if (!plainObject(value)) throw fail('layout', 'appearance must be a plain color object')
    for (const name of Object.keys(value)) {
      if (CONTROL_COLORS.has(name)) {
        if (!validControlColor(value[name])) throw fail('layout', 'Invalid appearance color: ' + name)
      } else if (states && CONTROL_STATES.has(name)) {
        validateAppearance(value[name], false)
      } else throw fail('layout', 'Unsupported appearance field: ' + name)
    }
  }

  function sliderValues(props) {
    const result = {
      value: props.value === undefined ? 0 : props.value,
      min: props.min === undefined ? 0 : props.min,
      max: props.max === undefined ? 100 : props.max,
      step: props.step === undefined ? 1 : props.step
    }
    for (const name of Object.keys(result)) {
      if (typeof result[name] !== 'number' || !Number.isFinite(result[name])) throw fail('layout', 'slider.' + name + ' must be finite')
    }
    if (result.min >= result.max || !Number.isFinite(result.max - result.min)) throw fail('layout', 'slider span must be finite and positive')
    if (result.step <= 0 || !Number.isFinite((result.max - result.min) / result.step)) throw fail('layout', 'slider.step must be positive with a finite step count')
    if (result.value < result.min || result.value > result.max) throw fail('layout', 'slider.value must be within [min, max]')
    return result
  }

  function validateWindowResponsiveRect(rect) {
    if (!plainObject(rect)) throw fail('layout', 'window.responsiveRect must be a plain object')
    function branch(value, name) {
      if (typeof value === 'number' && Number.isFinite(value) && ((name === 'x' || name === 'y') || value >= 0)) return
      if (typeof value === 'string' && /^(?:\d+(?:\.\d+)?|\.\d+)%$/.test(value)
        && Number.isFinite(Number(value.slice(0, -1))) && Number(value.slice(0, -1)) > 0) return
      throw fail('layout', 'window.responsiveRect.' + name + ' must be finite numeric geometry or a positive percentage')
    }
    for (const name of Object.keys(rect)) {
      if (!['x', 'y', 'width', 'height'].includes(name)) throw fail('layout', 'Unsupported window.responsiveRect field: ' + name)
      const value = rect[name]
      if (!plainObject(value)) { branch(value, name); continue }
      if (Object.keys(value).length === 0) throw fail('layout', 'window.responsiveRect.' + name + ' requires base or profiles')
      for (const field of Object.keys(value)) {
        if (field === 'base') branch(value.base, name)
        else if (field === 'profiles' && plainObject(value.profiles)) {
          for (const profile of Object.keys(value.profiles)) {
            if (!/^[1-6]$/.test(profile)) throw fail('layout', 'Window geometry profiles must be integers in [1, 6]')
            branch(value.profiles[profile], name)
          }
        } else throw fail('layout', 'Unsupported window geometry responsive field: ' + field)
      }
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
        if (WINDOW_EVENT_PROPS.has(name) && type !== 'window') throw fail('layout', name + ' is only supported by window')
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
    if (props.tooltip != null && typeof props.tooltip !== 'string') throw fail('layout', 'tooltip must be a string')
    if (props.appearance !== undefined) validateAppearance(props.appearance, true)
    if (type === 'slider') sliderValues(props)
    if (type === 'screen' && 'layout' in props && props.layout !== 'stack') throw fail('layout', 'screen.layout must be stack')
    if (type === 'panel' && 'workspacePresentation' in props && typeof props.workspacePresentation !== 'boolean') {
      throw fail('layout', 'panel.workspacePresentation must be boolean')
    }
    if (type === 'window') {
      if (typeof props.id !== 'string' || props.id.length === 0) throw fail('layout', 'window.id is required')
      if (typeof props.title !== 'string') throw fail('layout', 'window.title must be a string')
      for (const name of ['x', 'y', 'width', 'height']) {
        if (typeof props[name] !== 'number' || !Number.isFinite(props[name])) throw fail('layout', 'window.' + name + ' must be finite')
      }
      if (props.width < 40 || props.height < 20) throw fail('layout', 'window width must be at least 40 and height at least 20')
      for (const axis of ['Width', 'Height']) {
        const minimum = axis === 'Width' ? 40 : 20
        const min = props['min' + axis] === undefined ? minimum : props['min' + axis]
        const max = props['max' + axis]
        if (typeof min !== 'number' || !Number.isFinite(min) || min < minimum) throw fail('layout', 'window.min' + axis + ' must be finite and at least ' + minimum)
        if (max !== undefined && (typeof max !== 'number' || !Number.isFinite(max) || max < min)) throw fail('layout', 'window.max' + axis + ' must be finite and at least its minimum')
      }
      if (props.responsiveRect !== undefined) validateWindowResponsiveRect(props.responsiveRect)
      if (props.mode !== undefined && props.mode !== 'normal' && props.mode !== 'maximized') throw fail('layout', 'window.mode must be normal or maximized')
      for (const name of ['titlebarHeight', 'titlebarButtonWidth']) {
        const value = props[name]
        if (value !== undefined && (!Number.isInteger(value) || value < (name === 'titlebarHeight' ? 0 : 1))) {
          throw fail('layout', 'window.' + name + ' must be an integer at least ' + (name === 'titlebarHeight' ? 0 : 1))
        }
      }
      for (const name of ['active', 'draggable', 'closable', 'titlebarTitle', 'resizable', 'minimizable', 'maximizable']) {
        if (props[name] !== undefined && typeof props[name] !== 'boolean') throw fail('layout', 'window.' + name + ' must be a boolean')
      }
      if (props.coordinateSpace !== undefined && props.coordinateSpace !== 'logical') throw fail('layout', 'window geometry uses logical coordinates')
    }
    if (type === 'dialog') {
      if (typeof props.id !== 'string' || props.id.length === 0) throw fail('layout', 'dialog.id is required')
      if (props.title !== undefined && typeof props.title !== 'string') throw fail('layout', 'dialog.title must be a string')
      for (const name of ['closeOnEscape', 'closeOnBackdrop']) if (props[name] !== undefined && typeof props[name] !== 'boolean') throw fail('layout', name + ' must be a boolean')
    }
    if (type === 'item') {
      if (typeof props.item !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9/._-]+$/.test(props.item) || props.item === 'minecraft:air') throw fail('layout', 'item.item must be a non-air resource ID')
      if (props.count !== undefined && (!Number.isInteger(props.count) || props.count < 1 || props.count > 99)) throw fail('layout', 'item.count must be an integer in [1, 99]')
      if (props.itemTooltip !== undefined && typeof props.itemTooltip !== 'boolean') throw fail('layout', 'item.itemTooltip must be a boolean')
      if (props.tooltip !== undefined && typeof props.tooltip !== 'string') throw fail('layout', 'item.tooltip must be a string')
    }
    if (props.opacity != null && (typeof props.opacity !== 'number' || !Number.isFinite(props.opacity)
      || props.opacity < 0 || props.opacity > 1)) {
      throw fail('layout', 'opacity must be a number in [0, 1]')
    }
    if (props.truncate != null && typeof props.truncate !== 'boolean') throw fail('layout', 'truncate must be a boolean')
    if (props.crop != null && !validCrop(props.crop)) throw fail('layout', 'crop must be {x, y, width, height} or a 4-number array')
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
    // ponytail: the Java host currently supplies width/height only; convert more fields if it adds them.
    if (!plainObject(input) && input != null && typeof input.get === 'function') {
      input = { width: input.get('width'), height: input.get('height') }
    }
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

  function textMetrics(adapter, text, fontSize, maxWidth, font) {
    if (typeof adapter.measureText !== 'function') {
      throw fail('layout', 'UI host Adapter does not implement measureText; the common runtime never guesses text widths')
    }
    const result = font == null ? adapter.measureText(text, fontSize, maxWidth)
      : adapter.measureText(text, fontSize, maxWidth, font)
    if (result == null || !Number.isFinite(result.width) || !Number.isFinite(result.height)
      || result.width < 0 || result.height < 0) throw fail('layout', 'Font Adapter returned invalid text metrics')
    return result
  }

  function intrinsicSize(node, availableWidth, availableHeight, viewport, adapter) {
    const props = resolvedProps(node.props, viewport)
    if (props.visible === false) return { width: 0, height: 0 }
    const scale = props.coordinateSpace === 'design' ? viewport.designScale : 1
    if (node.type === 'dialog') return { width: 0, height: 0 }
    if (node.type === 'item') return { width: 16 * scale, height: 16 * scale }
    if (node.type === 'slider') return { width: 100 * scale, height: 20 * scale }
    if (node.type === '#text' || TEXT_CHILDREN.has(node.type)) {
      const text = String(props.text == null ? '' : props.text)
      const fontSize = props.fontSize == null ? 9 * scale : props.fontSize * scale
      const measured = textMetrics(adapter, text, fontSize, Math.max(0, availableWidth), props.font)
      return { width: measured.width, height: measured.height }
    }
    const children = node.children.filter(child => child.type !== 'dialog')
    if (children.length === 0) return { width: 0, height: 0 }
    const direction = props.direction || (node.type === 'row' ? 'row' : node.type === 'column' ? 'column' : 'column')
    const gap = Number(props.spacing == null ? (props.gap == null ? 0 : props.gap) : props.spacing) * scale
    const padding = Number(props.padding || 0) * scale
    const sizes = children.map(child => {
      const childProps = resolvedProps(child.props, viewport)
      const measured = intrinsicSize(child, availableWidth, availableHeight, viewport, adapter)
      if (childProps.visible === false) return measured
      const childScale = childProps.coordinateSpace === 'design' ? viewport.designScale : 1
      const preferred = (axis, intrinsic) => {
        const value = childProps[axis]
        const minimum = childProps[axis === 'width' ? 'minWidth' : 'minHeight']
        const maximum = childProps[axis === 'width' ? 'maxWidth' : 'maxHeight']
        // Relative and fill sizes remain deferred until the parent allocates its flow.
        return clampDimension(typeof value === 'number' ? value * childScale : intrinsic,
          typeof minimum === 'number' ? minimum : undefined,
          typeof maximum === 'number' ? maximum : undefined,
          axis === 'width' ? availableWidth : availableHeight, childScale, axis)
      }
      return { width: preferred('width', measured.width), height: preferred('height', measured.height) }
    })
    if (node.type === 'stack' || node.type === 'screen' && props.layout === 'stack') {
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

  function windowGeometry(props, width, height, profile) {
    if (props.mode === 'maximized') return {x: 0, y: 0, width, height}
    const policy = props.responsiveRect || {}
    function value(name, available) {
      const selected = resolveResponsiveValue(policy[name], profile, props[name])
      // Saturating percentages before multiplication avoids overflow; all geometry must fit this work area.
      return typeof selected === 'string' ? Math.min(100, Number(selected.slice(0, -1))) / 100 * available : selected
    }
    const shownWidth = Math.min(width, Math.max(props.minWidth === undefined ? 40 : props.minWidth,
      Math.min(props.maxWidth === undefined ? Infinity : props.maxWidth, value('width', width))))
    const shownHeight = Math.min(height, Math.max(props.minHeight === undefined ? 20 : props.minHeight,
      Math.min(props.maxHeight === undefined ? Infinity : props.maxHeight, value('height', height))))
    return {x: Math.max(0, Math.min(value('x', width), width - shownWidth)),
      y: Math.max(0, Math.min(value('y', height), height - shownHeight)), width: shownWidth, height: shownHeight}
  }

  function layoutNode(node, x, y, availableWidth, availableHeight, parentClip, viewport, adapter, diagnostics, rootNode, allocated, eventHandlers, ancestorVisible) {
    const props = resolvedProps(node.props, viewport)
    if (node.type === 'window') {
      if (props.titlebarHeight === undefined) props.titlebarHeight = 20
      if (props.titlebarButtonWidth === undefined) props.titlebarButtonWidth = 20
      if (props.titlebarTitle === undefined) props.titlebarTitle = true
      if (props.mode === undefined) props.mode = 'normal'
    }
    // Dialog is an out-of-flow portal, independent of ancestor bounds and scroll offsets.
    if (node.type === 'dialog') {
      x = viewport.safeArea.left
      y = viewport.safeArea.top
      availableWidth = viewport.contentWidth
      availableHeight = viewport.contentHeight
      parentClip = {x, y, width: availableWidth, height: availableHeight}
      allocated = true
    }
    const scale = props.coordinateSpace === 'design' ? viewport.designScale : 1
    const visible = ancestorVisible !== false && props.visible !== false
    const intrinsic = visible ? intrinsicSize(node, availableWidth, availableHeight, viewport, adapter) : { width: 0, height: 0 }
    let width = allocated || props.width === undefined ? availableWidth : dimension(props.width, availableWidth, intrinsic.width, scale)
    let height = allocated || props.height === undefined ? availableHeight : dimension(props.height, availableHeight, intrinsic.height, scale)
    if (!allocated) {
      width = clampDimension(width, props.minWidth, props.maxWidth, availableWidth, scale, 'width')
      height = clampDimension(height, props.minHeight, props.maxHeight, availableHeight, scale, 'height')
    }
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
    const titlebar = node.type === 'window' ? Math.min(props.titlebarHeight, height) : 0
    const innerY = y + titlebar + padding
    const innerWidth = Math.max(0, width - padding * 2)
    const innerHeight = Math.max(0, height - titlebar - padding * 2)
    const layoutClip = node.type === 'window' ? intersect(clip, {x, y: y + titlebar, width, height: Math.max(0, height - titlebar)}) : clip
    const stacked = node.type === 'stack' || node.type === 'dialog' || node.type === 'screen' && props.layout === 'stack'
    if (!visible) {
      for (const child of node.children) {
        children.push(layoutNode(child, x, y, 0, 0, layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers, false))
      }
    } else if (node.type === 'workspace') {
      for (const child of node.children) {
        const childProps = resolvedProps(child.props, viewport)
        const geometry = windowGeometry(childProps, width, height, viewport.profile)
        children.push(layoutNode(child, x + geometry.x, y + geometry.y, geometry.width, geometry.height,
          layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers))
      }
    } else if (node.children.length > 0 && node.type !== '#text' && !stacked && !TEXT_CHILDREN.has(node.type)) {
      const direction = props.direction || (node.type === 'row' ? 'row' : node.type === 'column' ? 'column' : 'column')
      const gap = Number(props.spacing == null ? (props.gap == null ? 0 : props.gap) : props.spacing) * scale
      const mainSize = direction === 'row' ? innerWidth : innerHeight
      const crossSize = direction === 'row' ? innerHeight : innerWidth
      const childRecords = node.children.filter(child => child.type !== 'dialog').map(child => {
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
        const childScale = record.props.coordinateSpace === 'design' ? viewport.designScale : 1
        if (record.rawMain !== 'fill') {
          const measured = dimension(record.rawMain, mainSize, direction === 'row' ? record.intrinsic.width : record.intrinsic.height, childScale)
          record.main = clampDimension(measured,
            direction === 'row' ? record.props.minWidth : record.props.minHeight,
            direction === 'row' ? record.props.maxWidth : record.props.maxHeight,
            mainSize, childScale, direction === 'row' ? 'width' : 'height')
        }
        fixedMain += record.main
      }
      const fillMain = fillCount === 0 ? 0 : Math.max(0, mainSize - fixedMain) / fillCount
      let usedMain = Math.max(0, childRecords.length - 1) * gap
      for (const record of childRecords) {
        const childScale = record.props.coordinateSpace === 'design' ? viewport.designScale : 1
        if (record.props.visible !== false && record.rawMain === 'fill') {
          record.main = clampDimension(fillMain,
            direction === 'row' ? record.props.minWidth : record.props.minHeight,
            direction === 'row' ? record.props.maxWidth : record.props.maxHeight,
            mainSize, childScale, direction === 'row' ? 'width' : 'height')
        }
        const intrinsicCross = direction === 'row' ? record.intrinsic.height : record.intrinsic.width
        record.cross = dimension(record.rawCross, crossSize, intrinsicCross, childScale)
        const align = record.props.align || props.align || 'start'
        if (align === 'stretch' && (record.rawCross === undefined || record.rawCross === 'auto' || record.rawCross === 'fill')) record.cross = crossSize
        record.cross = clampDimension(record.cross,
          direction === 'row' ? record.props.minHeight : record.props.minWidth,
          direction === 'row' ? record.props.maxHeight : record.props.maxWidth,
          crossSize, childScale, direction === 'row' ? 'height' : 'width')
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
          children.push(layoutNode(record.child, innerX, innerY, 0, 0, layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers))
          continue
        }
        const align = record.props.align || props.align || 'start'
        const crossOffset = align === 'center' ? Math.max(0, crossSize - record.cross) / 2 : align === 'end' ? Math.max(0, crossSize - record.cross) : 0
        const childX = direction === 'row' ? innerX + offset : innerX + crossOffset
        const childY = direction === 'row' ? innerY + crossOffset : innerY + offset
        const childWidth = direction === 'row' ? record.main : record.cross
        const childHeight = direction === 'row' ? record.cross : record.main
        const scrollOffset = node.type === 'scroll' ? Number(props.scrollOffset || 0) * scale : 0
        children.push(layoutNode(record.child, childX - (direction === 'row' ? scrollOffset : 0), childY - (direction === 'column' ? scrollOffset : 0), childWidth, childHeight, layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers))
        offset += record.main + actualGap
      }
    } else if (visible && stacked) {
      for (const child of node.children.filter(child => child.type !== 'dialog')) {
        const childIntrinsic = intrinsicSize(child, innerWidth, innerHeight, viewport, adapter)
        const childProps = resolvedProps(child.props, viewport)
        const childScale = childProps.coordinateSpace === 'design' ? viewport.designScale : 1
        const childWidth = clampDimension(dimension(childProps.width, innerWidth, childIntrinsic.width, childScale),
          childProps.minWidth, childProps.maxWidth, innerWidth, childScale, 'width')
        const childHeight = clampDimension(dimension(childProps.height, innerHeight, childIntrinsic.height, childScale),
          childProps.minHeight, childProps.maxHeight, innerHeight, childScale, 'height')
        const anchor = childProps.anchor || props.anchor || (node.type === 'dialog' ? 'center' : 'topLeft')
        const position = anchorOffset(anchor, innerWidth, innerHeight, childWidth, childHeight)
        children.push(layoutNode(child, innerX + position.x, innerY + position.y, childWidth, childHeight, layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers))
      }
    }
    if (visible && node.children.some(child => child.type === 'dialog')) {
      const regular = children.slice()
      children.length = 0
      let index = 0
      for (const child of node.children) children.push(child.type === 'dialog'
        ? layoutNode(child, innerX, innerY, innerWidth, innerHeight, layoutClip, viewport, adapter, diagnostics, false, true, eventHandlers)
        : regular[index++])
    }
    const bound = id != null ? eventHandlers.get(id) : null
    const bindings = bound == null ? Object.freeze([]) : Object.freeze(Object.keys(bound)
      .map(name => name.slice(2).toLowerCase()).sort())
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
      bindings: bindings,
      children: Object.freeze(children)
    })
  }

  function validateWorkspaceHierarchy(nodes) {
    let workspaces = 0
    let activeWindows = 0
    function visit(children, parent, depth, inheritedDesign) {
      for (const node of children) {
        const design = inheritedDesign || node.props.coordinateSpace === 'design'
        if (node.props.workspacePresentation === true && (node.type !== 'panel' || parent === null
          || parent.type !== 'screen' || depth !== 1 || !parent.children.some(sibling => sibling.type === 'workspace'))) {
          throw fail('layout', 'workspacePresentation panels must be direct root screen siblings of a workspace')
        }
        if (node.type === 'workspace') {
          if (++workspaces > 1 || parent !== null && (parent.type !== 'screen' || depth !== 1)) {
            throw fail('layout', 'workspace must be the root or a direct child of the root screen, with at most one workspace')
          }
          if (design) throw fail('layout', 'workspace geometry uses logical coordinates, including its ancestors')
        }
        if (node.type === 'window') {
          if (parent === null || parent.type !== 'workspace') throw fail('layout', 'window must be a direct workspace child')
          if (design) throw fail('layout', 'window geometry uses logical coordinates, including its ancestors')
          if (node.props.active === true && ++activeWindows > 1) throw fail('layout', 'workspace can have at most one active window')
        }
        if (parent !== null && parent.type === 'workspace' && node.type !== 'window') {
          throw fail('layout', 'workspace children must be windows')
        }
        visit(node.children, node, depth + 1, design)
      }
    }
    visit(nodes, null, 0, false)
  }

  function layoutSnapshotFor(nodes, viewport, adapter, eventHandlers) {
    validateWorkspaceHierarchy(nodes)
    const diagnostics = []
    const rootClip = Object.freeze({ x: viewport.safeArea.left, y: viewport.safeArea.top, width: viewport.contentWidth, height: viewport.contentHeight })
    const layoutNodes = nodes.map(node => layoutNode(node, rootClip.x, rootClip.y, rootClip.width, rootClip.height, rootClip, viewport, adapter, diagnostics, true, false, eventHandlers))
    return Object.freeze({ profile: viewport.profile, viewport: viewport, nodes: Object.freeze(layoutNodes), diagnostics: Object.freeze(diagnostics) })
  }

  function modalAllowsDispatch(snapshot, id, eventName, input) {
    if (snapshot === null) return true
    let modal = null
    let target = null
    let targetWindow = null
    function visit(nodes, containingWindow) {
      for (const node of nodes) {
        const window = node.type === 'window' ? node : containingWindow
        if (node.visible && node.type === 'dialog') modal = node
        if (node.id === id) { target = node; targetWindow = window }
        visit(node.children, window)
      }
    }
    visit(snapshot.nodes, null)
    if (target === null || !target.visible) return false
    if (target !== null && target.type === 'dialog' && eventName === 'onDismiss') {
      const reason = input == null ? undefined : input.reason
      if (reason === 'escape' && target.style.closeOnEscape === false || reason === 'backdrop' && target.style.closeOnBackdrop === false) return false
    }
    if (modal !== null) {
      function contains(node) {
        return node.visible && (node.id === id || node.children.some(contains))
      }
      // The global modal outranks workspace activation, even in an inactive window.
      return contains(modal)
    }
    if (WINDOW_EVENT_PROPS.has(eventName) && (target === null || target.type !== 'window')) return false
    if (targetWindow === null) return true
    if (!target.visible || !targetWindow.visible) return false
    if (target === targetWindow && eventName === 'onActivate') return true
    if (target === targetWindow && eventName === 'onDismiss') return target.style.closable !== false
    if (targetWindow.style.active !== true) return false
    if (target !== targetWindow) return true
    if (eventName === 'onMove') return target.style.draggable !== false && target.style.mode !== 'maximized'
    if (eventName === 'onResize') return target.style.resizable === true && target.style.mode !== 'maximized'
      && input != null && ['x', 'y', 'width', 'height'].every(name => Number.isFinite(input[name]))
      && input.width >= 0 && input.height >= 0 && WINDOW_RESIZE_EDGES.has(input.edge)
    if (eventName === 'onMinimize') return target.style.minimizable === true
    if (eventName === 'onMaximize') return target.style.maximizable === true && target.style.mode !== 'maximized'
    if (eventName === 'onRestore') return target.style.maximizable === true && target.style.mode === 'maximized'
    return true
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
    if (type === 'window') {
      hostProps.active = props.active === true
      hostProps.draggable = props.draggable !== false
      hostProps.closable = props.closable !== false
      for (const capability of ['resizable', 'minimizable', 'maximizable']) hostProps[capability] = props[capability] === true
    }
    if (type === 'slider') Object.assign(hostProps, sliderValues(props))
    if (type === 'item') { hostProps.count = props.count === undefined ? 1 : props.count; hostProps.itemTooltip = props.itemTooltip !== false }
    if (type === 'dialog') { hostProps.closeOnEscape = props.closeOnEscape !== false; hostProps.closeOnBackdrop = props.closeOnBackdrop !== false }
    return { props: Object.freeze(hostProps), children: children }
  }

  function expandSiblings(values, parentPath, eventHandlers, ids, scope = [], windowId = null, cancelled = null) {
    if (cancelled != null && cancelled()) return []
    const result = []
    const keys = new Set()
    let index = 0
    function visit(value) {
      if (cancelled != null && cancelled()) return
      if (value == null || typeof value === 'boolean') return
      if (Array.isArray(value)) {
        for (const item of value) visit(item)
        return
      }
      // Unkeyed fragments are transparent, including duplicate-key validation.
      if (isVNode(value) && value.type === FRAGMENT && value.key == null) {
        for (const item of value.children) visit(item)
        return
      }
      const position = index++
      if (typeof value === 'string' || typeof value === 'number') {
        result.push(Object.freeze({ type: '#text', key: null, identity: JSON.stringify([...scope, ['position', position]]), props: Object.freeze({ text: String(value) }), children: Object.freeze([]) }))
        return
      }
      if (!isVNode(value)) throw fail('render', 'Render values must be VNodes, arrays, text, or null')
      if (value.key != null) {
        if (keys.has(value.key)) throw fail('layout', 'Duplicate sibling key: ' + value.key)
        keys.add(value.key)
      }
      const identity = [...scope, value.key == null ? ['position', position] : ['key', value.key]]
      result.push(...expandNode(value, parentPath + '.' + position, eventHandlers, ids, identity, windowId, cancelled))
    }
    for (const value of values) visit(value)
    return result
  }

  function expandNode(node, path, eventHandlers, ids, identity, windowId, cancelled) {
    if (node.type === FRAGMENT) return expandSiblings(node.children, path, eventHandlers, ids, [...identity, ['fragment']], windowId, cancelled)
    if (typeof node.type === 'function') {
      let typeId = componentTypes.get(node.type)
      if (typeId == null) {
        typeId = nextComponentType++
        componentTypes.set(node.type, typeId)
      }
      let result
      try {
        result = callGuest(node.type, [node.props])
      } catch (error) {
        throw fail('component', 'Component render failed', error)
      }
      const expanded = expandSiblings([result], path, eventHandlers, ids, [...identity, ['component', typeId]], windowId, cancelled)
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
    else childNodes = expandSiblings(node.children, path, eventHandlers, ids, [], node.type === 'window' ? node.props.id : windowId, cancelled)
    // Local business ids are scoped after component expansion; window ids remain native ids.
    const props = windowId !== null && node.type !== 'window' && node.props.id != null
      ? {...node.props, id: JSON.stringify([windowId, node.props.id])} : node.props
    const normalized = hostPropsFor(node.type, props, childNodes)
    const id = normalized.props.id
    if (id != null) {
      if (ids.has(id)) throw fail('layout', 'Duplicate UI id: ' + id)
      ids.add(id)
    }
    const callbacks = Object.create(null)
    for (const name of EVENT_PROPS) {
      if (typeof node.props[name] === 'function') callbacks[name] = node.props[name]
    }
    if (id != null && node.props.disabled !== true && Object.keys(callbacks).length > 0) eventHandlers.set(id, Object.freeze(callbacks))
    const children = normalized.children
    return [Object.freeze({
      type: node.type,
      key: node.key,
      identity: JSON.stringify(identity),
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
    const oldByIdentity = new Map(previous.map(node => [node.identity, node]))
    const identities = new Set()
    const used = new Set()
    const next = []
    for (const node of candidate) {
      if (identities.has(node.identity)) throw fail('layout', 'Duplicate expanded UI identity')
      identities.add(node.identity)
      const matched = oldByIdentity.get(node.identity)
      const old = matched != null && matched.type === node.type && !used.has(matched) ? matched : null
      let handle
      if (old != null) {
        used.add(old)
        handle = old.handle
        transaction.update(handle, node.type, node.key, node.props)
      } else {
        handle = transaction.create(node.type, node.key, node.props)
      }
      const children = reconcileChildren(transaction, old == null ? [] : old.children, node.children, handle, state)
      next.push(Object.freeze({ type: node.type, key: node.key, identity: node.identity, props: node.props, children: children, handle: handle }))
      transaction.order(handle, children.map(child => child.handle))
    }
    for (const old of previous) if (!used.has(old)) removeTree(transaction, old)
    transaction.order(parent, next.map(node => node.handle))
    return Object.freeze(next)
  }

  function scheduleRoot(root) {
    if (root.disposed || root.pending || root.reconciling || !root.dirty) return
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

  function createSignal<T>(initialValue: T): NekoUiSignal<T> {
    let value = freezeValue(initialValue, new WeakSet())
    function observingRoots() {
      const roots = new Set(signalObservers.get(signal))
      for (let tracker = activeTracker; tracker != null; tracker = tracker.previous) {
        if (tracker.dependencies.has(signal)) roots.add(tracker.root)
      }
      return Array.from(roots).filter(root => !root.disposed)
    }
    const signal = {
      get: function () {
        if (activeTracker != null) activeTracker.dependencies.add(signal)
        return value
      },
      set: function (nextValue) {
        const next = freezeValue(nextValue, new WeakSet())
        const liveRoots = observingRoots()
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
        const liveRoots = observingRoots()
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

  function createStore<State extends Record<string, unknown>>(initialValue: State): NekoUiStore<State> {
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

  function batch<T>(callback: () => T): T {
    if (typeof callback !== 'function') throw new TypeError('batch expects a function')
    batchDepth++
    try {
      return callback()
    } finally {
      batchDepth--
      flushRoots()
    }
  }

  function createRoot(render: NekoUiRender, adapter: NekoUiHostAdapter, options?: NekoUiRootOptions): NekoUiRootHandle {
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
      reconciling: false,
      closing: false,
      forcedClearTree: null,
      reconcile: null
    }

    function reconcilePass(initial) {
      let candidate
      const previousTracker = activeTracker
      const tracker = { root: root, dependencies: new Set(), previous: previousTracker }
      activeTracker = tracker
      try {
        let output
        try {
          output = callGuest(render, [])
        } catch (error) {
          throw fail('render', 'UI render failed', error)
        }
        if (root.disposed) return false
        const eventHandlers = new Map()
        candidate = expandSiblings([output], rootId, eventHandlers, new Set(), [], null, () => root.disposed)
        if (root.disposed) return false
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
          layoutSnapshot = layoutSnapshotFor(candidate, root.viewport, adapter, eventHandlers)
          adapter.layout(cloneLayoutTree(candidate), root.viewport, { ...layoutSnapshot, rootId: rootId })
        } catch (error) {
          throw fail('layout', 'UI layout failed', error)
        }
        if (root.disposed) return false
        if (root.dirty) return 'stale'
        const transaction = adapter.begin()
        try {
          const nextTree = reconcileChildren(transaction, root.tree, candidate, null, root)
          if (root.disposed || root.dirty) {
            transaction.rollback()
            return root.disposed ? false : 'stale'
          }
          transaction.commit(nextTree.map(node => node.handle))
          if (root.disposed) {
            root.forcedClearTree = nextTree
            return true
          }
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

    root.reconcile = function (initial) {
      if (root.disposed) throw new Error('UI root is disposed: ' + rootId)
      if (root.reconciling || root.closing) {
        root.dirty = true
        return 'queued'
      }
      root.reconciling = true
      let committed = false
      try {
        // Only stable candidates publish. A commit callback may request a follow-up,
        // but its failure cannot turn an already published refresh into a failure.
        for (let pass = 0; pass < 32; pass++) {
          root.dirty = false
          pendingRoots.delete(root)
          const result = reconcilePass(initial && !committed)
          if (result === true) committed = true
          if (result === false || !root.dirty) return committed
        }
        const error = fail('render', 'UI updates did not stabilize after 32 passes')
        report(adapter, rootId, 'render', error)
        if (initial && !committed) throw error
        return committed
      } finally {
        root.reconciling = false
        root.dirty = false
        pendingRoots.delete(root)
        clearDeferredHostTree()
      }
    }

    function clearDeferredHostTree() {
      if (root.forcedClearTree == null) return
      const tree = root.forcedClearTree
      root.forcedClearTree = null
      clearHostTree(tree)
    }

    function detachRoot() {
      for (const signal of root.dependencies) signalObservers.get(signal).delete(root)
      root.dependencies.clear()
      root.eventHandlers.clear()
      root.tree = Object.freeze([])
      root.candidate = Object.freeze([])
      root.layoutSnapshot = null
      root.disposed = true
      root.dirty = false
      root.pending = false
      pendingRoots.delete(root)
      render = () => undefined
    }

    function clearHostTree(tree) {
      let transaction = null
      root.closing = true
      try {
        if (tree.length > 0) {
          transaction = adapter.begin()
          for (const node of tree) removeTree(transaction, node)
          transaction.order(null, [])
          transaction.commit([])
        }
        return true
      } catch (error) {
        try { if (transaction != null) transaction.rollback() } catch (_) {}
        report(adapter, rootId, 'host-update', error)
        return false
      } finally {
        root.closing = false
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
        if (root.reconciling || root.closing) return false
        const nextViewport = resolveViewport(input)
        if (root.candidate.length === 0) {
          root.viewport = nextViewport
          return false
        }
        root.reconciling = true
        try {
          const nextSnapshot = layoutSnapshotFor(root.candidate, nextViewport, adapter, root.eventHandlers)
          if (root.disposed) return false
          adapter.layout(cloneLayoutTree(root.candidate), nextViewport, { ...nextSnapshot, rootId: rootId }, true)
          if (root.disposed) return false
          root.viewport = nextViewport
          root.layoutSnapshot = nextSnapshot
          return true
        } catch (error) {
          report(adapter, rootId, 'layout', fail('layout', 'UI resize layout failed', error))
          return false
        } finally {
          root.reconciling = false
          clearDeferredHostTree()
          if (!root.disposed && root.dirty) {
            pendingRoots.add(root)
            flushRoots()
          }
        }
      },
      layout: function () { return root.layoutSnapshot },
      dispatch: function (id, eventName, input) {
        if (root.disposed) return false
        const callbackName = EVENT_PROPS.find(name => name.slice(2).toLowerCase() === String(eventName).toLowerCase())
        if (callbackName == null) throw new TypeError('Unknown UI event: ' + eventName)
        if (!modalAllowsDispatch(root.layoutSnapshot, id, callbackName, input)) return false
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
          width: input == null ? undefined : input.width,
          height: input == null ? undefined : input.height,
          edge: input == null ? undefined : input.edge,
          button: input == null ? undefined : input.button,
          key: input == null ? undefined : input.key,
          value: input == null ? undefined : input.value,
          reason: input == null ? undefined : input.reason,
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
      isUpdating: function () { return !root.disposed && (root.reconciling || root.closing) },
      close: function () {
        if (root.disposed) return false
        if (!ownerThread(adapter)) {
          if (!adapter.enqueue(() => handle.close())) throw new Error('UI close rejected off the owner thread')
          return 'queued'
        }
        if (root.reconciling || root.closing) return false
        if (!clearHostTree(root.tree)) return false
        detachRoot()
        return true
      },
      forceClose: function () {
        if (root.disposed) return
        if (!ownerThread(adapter)) throw new Error('UI forced close requires the owner thread')
        if (root.reconciling) {
          // Disposal is immediate; host cleanup waits for the active transaction.
          root.forcedClearTree = root.tree
          detachRoot()
          return
        }
        try {
          handle.close()
        } finally {
          // Generation teardown must release guest state even if host cleanup fails.
          detachRoot()
        }
      }
    })
    root.reconcile(true)
    return handle
  }

  function controlProps(props, fields, callbacks) {
    if (!plainObject(props)) throw fail('component', 'Control props must be a plain object')
    const allowed = new Set([...SHARED_PROPS, 'key', 'disabled', 'tooltip', 'appearance', ...fields, ...callbacks])
    for (const name of Object.keys(props)) {
      if (!allowed.has(name)) throw fail('component', 'Unsupported control prop: ' + name)
    }
    if (typeof props.id !== 'string' || props.id.length === 0) throw fail('component', 'Control id must be a non-empty string')
    for (const name of callbacks) {
      if (typeof props[name] !== 'function') throw fail('component', name + ' must be a function')
    }
    const forwarded = Object.create(null)
    for (const name of [...SHARED_PROPS, 'disabled', 'tooltip', 'appearance']) {
      if (Object.prototype.hasOwnProperty.call(props, name)) forwarded[name] = props[name]
    }
    validateProps('button', forwarded)
    return forwarded
  }

  function validationMessage(message, fallback) {
    if (message !== undefined && typeof message !== 'string') throw new TypeError('Validator message must be a string')
    return message === undefined ? fallback : message
  }

  function validationLength(length) {
    if (!Number.isSafeInteger(length) || length < 0) throw new TypeError('Validator length must be a non-negative safe integer')
    return length
  }

  const Validators: NekoUiValidators = Object.freeze({
    required: function (message) {
      const error = validationMessage(message, 'Required')
      return value => value == null || typeof value === 'string' && value.trim().length === 0 ? error : null
    },
    minLength: function (length, message) {
      validationLength(length)
      const error = validationMessage(message, 'Minimum length is ' + length)
      return value => typeof value === 'string' && value.length >= length ? null : error
    },
    maxLength: function (length, message) {
      validationLength(length)
      const error = validationMessage(message, 'Maximum length is ' + length)
      return value => typeof value === 'string' && value.length <= length ? null : error
    },
    numberRange: function (min, max, message) {
      if (typeof min !== 'number' || typeof max !== 'number' || !Number.isFinite(min) || !Number.isFinite(max) || min > max) {
        throw new TypeError('Validator number range must have finite ordered endpoints')
      }
      const error = validationMessage(message, 'Number must be between ' + min + ' and ' + max)
      return value => {
        if (typeof value !== 'number' && (typeof value !== 'string' || value.trim().length === 0)) return error
        const number = Number(value)
        return Number.isFinite(number) && number >= min && number <= max ? null : error
      }
    }
  })

  const validateForm: NekoUiValidateForm = function (values, rules) {
    if (!plainObject(values) || !plainObject(rules)) throw new TypeError('Form values and rules must be plain objects')
    const snapshot = Object.create(null)
    for (const name of Object.keys(values)) {
      const value = values[name]
      if (value !== null && typeof value !== 'string' && typeof value !== 'boolean' && (typeof value !== 'number' || !Number.isFinite(value))) {
        throw new TypeError('Form values must be finite scalar values: ' + name)
      }
      snapshot[name] = value
    }
    Object.freeze(snapshot)
    // Snapshot every rule list before executing user callbacks, so mutation cannot change this validation pass.
    const fields = Object.keys(rules).map(name => {
      const callbacks = rules[name]
      if (!Array.isArray(callbacks) || callbacks.some(callback => typeof callback !== 'function')) throw new TypeError('Form rules must be arrays of functions: ' + name)
      return {name, callbacks: callbacks.slice()}
    })
    const errors = Object.create(null)
    for (const field of fields) {
      const failures = []
      for (const callback of field.callbacks) {
        const result = callback(snapshot[field.name], snapshot)
        if (result !== null && result !== undefined && typeof result !== 'string') throw new TypeError('Form validators must return a string, null or undefined')
        if (typeof result === 'string') failures.push(result)
      }
      if (failures.length > 0) errors[field.name] = Object.freeze(failures)
    }
    return Object.freeze({valid: Object.keys(errors).length === 0, errors: Object.freeze(errors)})
  }

  const Toggle: NekoUiToggle = function (props) {
    const forwarded = controlProps(props, ['checked', 'label', 'text'], ['onChange'])
    if (typeof props.checked !== 'boolean') throw fail('component', 'Toggle.checked must be a boolean')
    if (props.label !== undefined && typeof props.label !== 'string') throw fail('component', 'Toggle.label must be a string')
    if (props.text !== undefined && typeof props.text !== 'string') throw fail('component', 'Toggle.text must be a string')
    return UI.element('button', Object.assign(forwarded, {
      text: (props.checked ? '[x] ' : '[ ] ') + (props.label === undefined ? (props.text || '') : props.label),
      onClick: () => { if (!props.disabled) props.onChange(!props.checked) }
    }))
  }

  const Slider: NekoUiSlider = function (props) {
    const forwarded = controlProps(props, ['value', 'min', 'max', 'step'], ['onChange'])
    if (props.value === undefined) throw fail('component', 'Slider.value is required')
    const values = sliderValues(props)
    return UI.element('slider', Object.assign(forwarded, values, {
      onChange: event => {
        if (props.disabled) return
        if (typeof event.value !== 'string' || event.value.trim().length === 0) throw fail('event', 'Slider change value must be a numeric string')
        const value = Number(event.value)
        sliderValues(Object.assign({}, values, { value }))
        props.onChange(value)
      }
    }))
  }

  // Select expands inline; controlled state belongs to the caller, not this render.
  const Select: NekoUiSelect = function (props) {
    const forwarded = controlProps(props, ['value', 'options', 'expanded'], ['onChange', 'onExpandedChange'])
    if (typeof props.value !== 'string') throw fail('component', 'Select.value must be a string')
    if (typeof props.expanded !== 'boolean') throw fail('component', 'Select.expanded must be a boolean')
    if (!Array.isArray(props.options)) throw fail('component', 'Select.options must be an array')
    const values = new Set()
    let selected = null
    for (const option of props.options) {
      if (!plainObject(option) || Object.keys(option).some(name => !['value', 'label', 'disabled'].includes(name))) throw fail('component', 'Select options must be plain value/label/disabled objects')
      if (typeof option.value !== 'string' || typeof option.label !== 'string') throw fail('component', 'Select option value and label must be strings')
      if (option.disabled !== undefined && typeof option.disabled !== 'boolean') throw fail('component', 'Select option disabled must be a boolean')
      if (values.has(option.value)) throw fail('component', 'Duplicate Select option value: ' + option.value)
      values.add(option.value)
      if (option.value === props.value) selected = option
    }
    if (selected == null && (props.options.length !== 0 || props.value !== '')) throw fail('component', 'Select.value must identify an option')
    const visual = { disabled: props.disabled === true }
    if (props.tooltip !== undefined) visual.tooltip = props.tooltip
    if (props.appearance !== undefined) visual.appearance = props.appearance
    if (props.coordinateSpace !== undefined) visual.coordinateSpace = props.coordinateSpace
    const trigger = UI.element('button', Object.assign({}, visual, {
      id: props.id, width: 'fill', height: 20, text: selected == null ? '' : selected.label,
      onClick: () => { if (!props.disabled) props.onExpandedChange(!props.expanded) }
    }))
    const options = props.expanded ? UI.element('scroll', {
      id: props.id + ':options', width: 'fill', height: Math.min(props.options.length, 6) * 20, scrollY: true,
      coordinateSpace: props.coordinateSpace || 'logical',
      children: props.options.map(option => UI.element('button', Object.assign({}, visual, {
        id: props.id + ':option:' + encodeURIComponent(option.value), key: option.value,
        width: 'fill', height: 20, text: option.label, disabled: props.disabled === true || option.disabled === true,
        onClick: () => {
          if (!props.disabled && !option.disabled) UI.batch(() => {
            props.onChange(option.value)
            props.onExpandedChange(false)
          })
        }
      })))
    }) : null
    const layout = Object.create(null)
    for (const name of SHARED_PROPS) if (Object.prototype.hasOwnProperty.call(forwarded, name)) layout[name] = forwarded[name]
    layout.id = props.id + ':root'
    // Only Select supplies its natural height; shared container measurement stays unchanged.
    const heights = Object.create(null)
    for (let profile = 1; profile <= 6; profile++) {
      const explicitHeight = resolveResponsiveValue(layout.height, profile, undefined)
      const padding = resolveResponsiveValue(layout.padding, profile, 0)
      const spacing = resolveResponsiveValue(layout.spacing, profile, undefined)
      const gap = spacing === undefined ? resolveResponsiveValue(layout.gap, profile, 0) : spacing
      const direction = resolveResponsiveValue(layout.direction, profile, 'column')
      const listHeight = Math.min(props.options.length, 6) * 20
      const contentHeight = !props.expanded ? 20 : direction === 'row' ? Math.max(20, listHeight) : 20 + listHeight + gap
      heights[profile] = explicitHeight === undefined || explicitHeight === 'auto' ? contentHeight + padding * 2 : explicitHeight
    }
    layout.height = { profiles: heights }
    layout.children = [trigger, options]
    return UI.element('column', layout)
  }

  const Tabs: NekoUiTabs = function (props) {
    const forwarded = controlProps(props, ['value', 'tabs'], ['onChange'])
    if (typeof props.value !== 'string' || !Array.isArray(props.tabs) || props.tabs.length === 0) throw fail('component', 'Tabs requires a string value and an array of tabs')
    const values = new Set()
    let selected = null
    for (const tab of props.tabs) {
      if (!plainObject(tab) || Object.keys(tab).some(name => !['value', 'label', 'disabled', 'render'].includes(name))) throw fail('component', 'Tabs entries must contain only value, label, disabled and render')
      if (typeof tab.value !== 'string' || typeof tab.label !== 'string' || typeof tab.render !== 'function') throw fail('component', 'Tabs entries require string value/label and a render function')
      if (tab.disabled !== undefined && typeof tab.disabled !== 'boolean') throw fail('component', 'Tabs disabled must be a boolean')
      if (values.has(tab.value)) throw fail('component', 'Duplicate Tabs value: ' + tab.value)
      values.add(tab.value)
      if (tab.value === props.value) selected = tab
    }
    if (selected === null) throw fail('component', 'Tabs.value must identify a tab')
    const change = value => { if (!props.disabled && value !== props.value) props.onChange(value) }
    const keyChange = event => {
      if (props.disabled || typeof event.key !== 'string') return
      const enabled = props.tabs.filter(tab => !tab.disabled)
      if (enabled.length === 0) return
      const key = event.key.toLowerCase()
      const current = enabled.findIndex(tab => tab.value === props.value)
      let next = current
      if (key === 'home') next = 0
      else if (key === 'end') next = enabled.length - 1
      else if (key === 'right' || key === 'down') next = (current + 1) % enabled.length
      else if (key === 'left' || key === 'up') next = ((current < 0 ? enabled.length : current) + enabled.length - 1) % enabled.length
      else return
      change(enabled[next].value)
    }
    const visual = {coordinateSpace: props.coordinateSpace || 'logical'}
    if (props.tooltip !== undefined) visual.tooltip = props.tooltip
    if (props.appearance !== undefined) visual.appearance = props.appearance
    const headers = UI.element('row', {id: props.id + ':tabs', width: 'fill', height: 20, coordinateSpace: visual.coordinateSpace,
      children: props.tabs.map(tab => UI.element('button', Object.assign({}, visual, {
        id: props.id + ':tab:' + encodeURIComponent(tab.value), key: tab.value,
        width: Math.max(40, (tab.label.length + 2) * 6 + 16), height: 20, text: tab.value === props.value ? '[' + tab.label + ']' : tab.label,
        disabled: props.disabled === true || tab.disabled === true,
        onClick: () => { if (!tab.disabled) change(tab.value) }, onKey: keyChange
      })))
    })
    const content = UI.element('column', {id: props.id + ':content', width: 'fill', height: 'fill', coordinateSpace: visual.coordinateSpace,
      children: selected === null ? null : selected.render()})
    const layout = Object.create(null)
    for (const name of SHARED_PROPS) if (Object.prototype.hasOwnProperty.call(forwarded, name)) layout[name] = forwarded[name]
    if (layout.width === undefined) layout.width = 'fill'
    if (layout.height === undefined) layout.height = 'fill'
    layout.children = [headers, content]
    return UI.element('column', layout)
  }

  const Dialog: NekoUiDialog = function (props) {
    if (!plainObject(props) || Object.keys(props).some(name => !['id', 'key', 'open', 'onOpenChange', 'title', 'children', 'width', 'height', 'closeOnEscape', 'closeOnBackdrop'].includes(name))) throw fail('component', 'Unsupported Dialog props')
    if (typeof props.id !== 'string' || props.id.length === 0 || typeof props.open !== 'boolean' || typeof props.onOpenChange !== 'function') throw fail('component', 'Dialog requires id, open and onOpenChange')
    if (props.title !== undefined && typeof props.title !== 'string') throw fail('component', 'Dialog.title must be a string')
    for (const name of ['closeOnEscape', 'closeOnBackdrop']) if (props[name] !== undefined && typeof props[name] !== 'boolean') throw fail('component', name + ' must be a boolean')
    const panel = {id: props.id + ':panel', anchor: 'center', width: props.width === undefined ? '80%' : props.width,
      height: props.height === undefined ? '80%' : props.height, padding: 8,
      background: '#202020', borderColor: '#808080', borderWidth: 1,
      children: [props.title === undefined ? null : UI.element('label', {text: props.title, height: 12, width: 'fill'}),
        UI.element('column', {id: props.id + ':content', width: 'fill', height: 'fill', children: props.children === undefined ? null : props.children})]}
    validateProps('panel', panel)
    if (!props.open) return null
    return UI.element('dialog', {id: props.id, title: props.title === undefined ? '' : props.title,
      closeOnEscape: props.closeOnEscape !== false, closeOnBackdrop: props.closeOnBackdrop !== false,
      onDismiss: () => props.onOpenChange(false), children: UI.element('panel', panel)})
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

  const typedJsx: NekoUiJsxFactory = jsx
  const typedJsxs: NekoUiJsxFactory = jsxs
  globalThis.__nekoNodeDefine(['nekojs/jsx-runtime'], {
    jsx: typedJsx,
    jsxs: typedJsxs,
    Fragment: FRAGMENT,
    Toggle: Toggle,
    Slider: Slider,
    Select: Select,
    Tabs: Tabs,
    Dialog: Dialog,
    validateForm: validateForm,
    Validators: Validators,
    UI: UI
  })
})()
