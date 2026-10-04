declare module 'nekojs/jsx-runtime' {
    export const jsx: NekoUiJsxFactory;
    export const jsxs: NekoUiJsxFactory;
    export const Fragment: NekoUiFragment;
    export const UI: NekoUiApi;
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
        layout(tree: readonly NekoUiHostNode[], viewport?: NekoUiViewport, snapshot?: NekoUiLayoutSnapshot, publish?: boolean): void
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
    interface NekoUiSharedProps {
        id?: string
        key?: string | number
        children?: unknown
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
    type NekoUiCrop = { readonly x: number; readonly y: number; readonly width: number; readonly height: number } | readonly [number, number, number, number]
    type NekoUiPrimitivePropsByType = {
        screen: NekoUiSharedProps & { title?: string; pausesGame?: boolean; closeOnEscape?: boolean }
        panel: NekoUiSharedProps & { background?: string | number; borderColor?: string | number; borderWidth?: number; radius?: number; opacity?: number }
        row: NekoUiSharedProps
        column: NekoUiSharedProps
        stack: NekoUiSharedProps
        scroll: NekoUiSharedProps & { scrollX?: NekoUiResponsive<boolean>; scrollY?: NekoUiResponsive<boolean>; scrollOffset?: NekoUiResponsive<number> }
        label: NekoUiSharedProps & { text?: string; color?: string | number; fontSize?: NekoUiResponsive<number>; wrap?: boolean; truncate?: boolean }
        button: NekoUiSharedProps & { text?: string; disabled?: boolean; tooltip?: string }
        input: NekoUiSharedProps & { value?: string; placeholder?: string; maxLength?: number; disabled?: boolean }
        image: NekoUiSharedProps & { resource?: string; fit?: 'contain' | 'cover' | 'stretch'; opacity?: number; icon?: string; crop?: NekoUiCrop }
        spacer: NekoUiSharedProps
      }
    type NekoUiPrimitiveProps = NekoUiPrimitivePropsByType[NekoUiPrimitive]
    type NekoUiElementProps<T extends NekoUiJsxType> = T extends NekoUiPrimitive ? NekoUiPrimitivePropsByType[T] : object
    type NekoUiJsxFactory = <T extends NekoUiJsxType>(type: T, props: NekoUiElementProps<T> | null, key?: string | number) => NekoVNode
    interface NekoUiFragment {
        (props: object): NekoVNode
      }
    export namespace JSX {
        interface Element extends NekoVNode {}
        interface ElementChildrenAttribute { children: {} }
        interface IntrinsicElements {
          screen: NekoUiPrimitivePropsByType['screen']
          panel: NekoUiPrimitivePropsByType['panel']
          row: NekoUiPrimitivePropsByType['row']
          column: NekoUiPrimitivePropsByType['column']
          stack: NekoUiPrimitivePropsByType['stack']
          scroll: NekoUiPrimitivePropsByType['scroll']
          label: NekoUiPrimitivePropsByType['label']
          button: NekoUiPrimitivePropsByType['button']
          input: NekoUiPrimitivePropsByType['input']
          image: NekoUiPrimitivePropsByType['image']
          spacer: NekoUiPrimitivePropsByType['spacer']
        }
      }
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
        createStore(initial: Record<string, unknown>): NekoUiStore
        createRoot(render: NekoUiCallback, adapter: NekoUiHostAdapter, options?: NekoUiRootOptions): NekoUiRootHandle
        batch<T>(callback: () => T): T
        readonly fragment: NekoUiFragment
      }

}
