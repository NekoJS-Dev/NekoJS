// Auto-generated NekoJS JSX UI declarations from the Java-side UI registry.
// Do not edit; the registry (UiPrimitiveRegistry/UiProp) is the single source.

declare namespace NekoJsx {
  type UiAlignment = 'start' | 'center' | 'end' | 'space-between' | 'space-around';
  type UiAnchor = 'top-left' | 'top-center' | 'top-right' | 'center-left' | 'center' | 'center-right' | 'bottom-left' | 'bottom-center' | 'bottom-right';
  type UiEventName = 'click' | 'mouseDown' | 'mouseUp' | 'mouseMove' | 'scroll' | 'keyDown' | 'keyUp' | 'char' | 'focus' | 'blur' | 'submit' | 'close';

  interface UiSize { readonly width: number; readonly height: number }
  interface UiRect { readonly x: number; readonly y: number; readonly width: number; readonly height: number }
  interface UiProps {
    readonly [name: string]: unknown;
    readonly children?: unknown;
  }

  interface UiRootHandle {
    readonly rootId: string;
    readonly generation: number;
    readonly status: 'created' | 'open' | 'invalidated' | 'disposed';
    readonly isActive: boolean;
    open(): void;
    invalidate(): void;
    invalidateAndReconcile(): void;
    dispatchEvent(event: UiEvent): void;
    dispose(): void;
    readonly focusedPath: string | null;
  }

  interface UiEvent {
    readonly type: UiEventName;
    readonly mouseX: number;
    readonly mouseY: number;
    readonly button: number;
    readonly scrollDelta: number;
    readonly keyCode: number;
    readonly key: string | null;
    readonly character: string | null;
    readonly shift: boolean;
    readonly control: boolean;
    readonly alt: boolean;
    readonly targetPath: string | null;
    readonly rootId: string | null;
    readonly generation: number;
  }

  interface UiSignal<T> {
    get(): T;
    set(value: T): void;
    update(updater: (previous: T) => T): void;
  }

  interface UiStore {
    read(key: string): unknown;
    write(key: string, value: unknown): void;
    merge(values: Record<string, unknown>): void;
    field(key: string): { get(): unknown };
    snapshot(): Record<string, unknown>;
  }

  interface UiStateOwner {
    createSignal<T>(initial: T): UiSignal<T>;
    createStore(initial: Record<string, unknown>): UiStore;
    runTick(): void;
    dispose(): void;
    readonly isDisposed: boolean;
  }

  interface UiFacade {
    readonly generation: number;
    createStateOwner(): UiStateOwner;
    create(screen: UiScreen): UiRootHandle;
    open(screen: UiScreen): UiRootHandle;
    invalidateGeneration(generation: number): void;
    closeGeneration(generation: number): void;
    roots(): UiRootHandle[];
    dispose(): void;
  }

  interface UiScreenOptions {
    readonly title: string;
    readonly pauseGame: boolean;
    readonly closeOnEscape: boolean;
    readonly closeOnExternal: boolean;
  }

  interface UiScreen {
    render(): NekoJsxElement | null;
    readonly options?: UiScreenOptions;
  }

  interface UiPrimitiveProps {
    readonly 'screen': { /* content */ readonly title?: string; /* layout */ readonly align?: NekoJsx.UiAlignment; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; /* event */ readonly onClose?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'panel': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; /* state */ readonly disabled?: boolean; readonly focused?: boolean; readonly hovered?: boolean; readonly loading?: boolean; readonly pressed?: boolean; /* event */ readonly onBlur?: (event: NekoJsx.UiEvent) => void | null; readonly onClick?: (event: NekoJsx.UiEvent) => void | null; readonly onFocus?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseDown?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseMove?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseUp?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'row': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly gap?: number; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; };
    readonly 'column': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly gap?: number; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; };
    readonly 'stack': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; };
    readonly 'scroll': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly scrollX?: number; readonly scrollY?: number; readonly scrollable?: boolean; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; /* state */ readonly disabled?: boolean; readonly focused?: boolean; readonly hovered?: boolean; readonly pressed?: boolean; /* event */ readonly onBlur?: (event: NekoJsx.UiEvent) => void | null; readonly onClick?: (event: NekoJsx.UiEvent) => void | null; readonly onFocus?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseDown?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseMove?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseUp?: (event: NekoJsx.UiEvent) => void | null; readonly onScroll?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'label': { /* content */ readonly text?: string; readonly wrap?: boolean; /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly color?: string; readonly font?: string; readonly fontSize?: number; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; /* event */ readonly onClick?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'button': { /* content */ readonly text?: string; /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly color?: string; readonly font?: string; readonly fontSize?: number; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; /* state */ readonly disabled?: boolean; readonly focused?: boolean; readonly hovered?: boolean; readonly loading?: boolean; readonly pressed?: boolean; /* event */ readonly onBlur?: (event: NekoJsx.UiEvent) => void | null; readonly onClick?: (event: NekoJsx.UiEvent) => void | null; readonly onClose?: (event: NekoJsx.UiEvent) => void | null; readonly onFocus?: (event: NekoJsx.UiEvent) => void | null; readonly onKeyDown?: (event: NekoJsx.UiEvent) => void | null; readonly onKeyUp?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseDown?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseMove?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseUp?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'input': { /* content */ readonly placeholder?: string; readonly value?: string; /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly color?: string; readonly font?: string; readonly fontSize?: number; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; /* state */ readonly disabled?: boolean; readonly error?: string | boolean; readonly focused?: boolean; readonly hovered?: boolean; readonly maxLength?: number; readonly pressed?: boolean; /* event */ readonly onBlur?: (event: NekoJsx.UiEvent) => void | null; readonly onChar?: (event: NekoJsx.UiEvent) => void | null; readonly onClick?: (event: NekoJsx.UiEvent) => void | null; readonly onClose?: (event: NekoJsx.UiEvent) => void | null; readonly onFocus?: (event: NekoJsx.UiEvent) => void | null; readonly onKeyDown?: (event: NekoJsx.UiEvent) => void | null; readonly onKeyUp?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseDown?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseMove?: (event: NekoJsx.UiEvent) => void | null; readonly onMouseUp?: (event: NekoJsx.UiEvent) => void | null; readonly onSubmit?: (event: NekoJsx.UiEvent) => void | null; };
    readonly 'image': { /* content */ readonly fit?: 'stretch' | 'contain' | 'cover' | 'repeat'; readonly src?: string; /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; /* accessibility */ readonly narration?: string; readonly tooltip?: string; };
    readonly 'spacer': { /* layout */ readonly align?: NekoJsx.UiAlignment; readonly anchor?: NekoJsx.UiAnchor; readonly grow?: number; readonly height?: number | 'auto' | 'fill' | `${number}%`; readonly justify?: NekoJsx.UiAlignment; readonly margin?: number; readonly maxHeight?: number; readonly maxWidth?: number; readonly minHeight?: number; readonly minWidth?: number; readonly padding?: number; readonly visible?: boolean; readonly width?: number | 'auto' | 'fill' | `${number}%`; /* visual */ readonly background?: string; readonly border?: string; readonly opacity?: number; readonly radius?: number; };
  }

  type UiPrimitiveName = 'screen' | 'panel' | 'row' | 'column' | 'stack' | 'scroll' | 'label' | 'button' | 'input' | 'image' | 'spacer';
  type UiEventPropName = 'onClick' | 'onMouseDown' | 'onMouseUp' | 'onMouseMove' | 'onScroll' | 'onKeyDown' | 'onKeyUp' | 'onChar' | 'onFocus' | 'onBlur' | 'onSubmit' | 'onClose';
}

declare global {
  const Ui: NekoJsx.UiFacade;

  interface NekoJsxElement {
    readonly kind: 'jsx' | 'jsxs';
    readonly type: unknown;
    readonly key: string | null;
    readonly props: NekoJsx.UiProps;
    readonly children: readonly unknown[];
  }

  namespace JSX {
    interface Element extends NekoJsxElement {}
    interface ElementChildrenAttribute { children: {} }
    interface IntrinsicElements extends NekoJsx.UiPrimitiveProps {}
  }
}
