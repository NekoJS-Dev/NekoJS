declare module 'nekojs/jsx-runtime' {
    export const jsx: NekoUiJsxFactory;
    export const jsxs: NekoUiJsxFactory;
    export const Fragment: NekoUiFragment;
    export const Toggle: NekoUiToggle;
    export const Slider: NekoUiSlider;
    export const Select: NekoUiSelect;
    export const Tabs: NekoUiTabs;
    export const Dialog: NekoUiDialog;
    export const validateForm: NekoUiValidateForm;
    export const Validators: NekoUiValidators;
    export const UI: NekoUiApi;
    type NekoUiProfile = 1 | 2 | 3 | 4 | 5 | 6
    type NekoUiPrimitive = 'screen' | 'panel' | 'row' | 'column' | 'stack' | 'scroll' | 'label' | 'button' | 'input' | 'slider' | 'image' | 'spacer' | 'dialog' | 'item' | 'workspace' | 'window'
    type NekoUiJsxType = NekoUiPrimitive | NekoUiComponent | symbol
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
        readonly type: string | symbol | NekoUiComponent
        readonly key: string | null
        readonly props: Readonly<Record<string, unknown>>
        readonly children: readonly unknown[]
      }
    type NekoUiComponent = Function
    type NekoUiCallback = Function
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
        layout(tree: readonly NekoUiHostNode[], viewport?: NekoUiViewport, snapshot?: NekoUiLayoutSnapshot, publish?: boolean): void
        measureText?(text: string, fontSize: number, maxWidth: number, font?: string): { readonly width: number; readonly height: number }
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
        isUpdating(): boolean
        close(): boolean | 'queued'
        forceClose(): void
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
    export namespace JSX {
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
