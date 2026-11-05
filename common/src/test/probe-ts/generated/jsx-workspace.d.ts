declare module 'nekojs/jsx-workspace' {
    export const createWorkspace: NekoWorkspaceFactory;
    type NekoWorkspaceHost = import('nekojs/jsx-runtime').NekoUiHostAdapter & {
      bindRoot(root: import('nekojs/jsx-runtime').NekoUiRootHandle): void
      open(): void
      close(): boolean
    }
    type NekoWorkspaceResult = boolean | 'queued'
    type NekoWorkspaceRender = import('nekojs/jsx-runtime').NekoUiRender
    type NekoWorkspaceRect = import('nekojs/jsx-runtime').NekoUiRect
    type NekoWorkspaceWindowMode = 'normal' | 'minimized' | 'maximized'
    type NekoWorkspaceResponsiveRect = import('nekojs/jsx-runtime').NekoUiWindowResponsiveRect
    interface NekoWorkspaceWindowConfig {
      readonly title?: string
      readonly rect?: NekoWorkspaceRect
      readonly visible?: boolean
      readonly draggable?: boolean
      readonly closable?: boolean
      readonly minimizable?: boolean
      readonly maximizable?: boolean
      readonly resizable?: boolean
      readonly titlebarHeight?: number
      readonly titlebarButtonWidth?: number
      readonly titlebarTitle?: boolean
      readonly responsiveRect?: NekoWorkspaceResponsiveRect | null
      readonly minWidth?: number
      readonly minHeight?: number
      readonly maxWidth?: number
      readonly maxHeight?: number
    }
    interface NekoWorkspaceWindowPatch {
      readonly title?: string
      readonly rect?: Partial<NekoWorkspaceRect>
      readonly visible?: boolean
      readonly draggable?: boolean
      readonly closable?: boolean
      readonly minimizable?: boolean
      readonly maximizable?: boolean
      readonly resizable?: boolean
      readonly titlebarHeight?: number
      readonly titlebarButtonWidth?: number
      readonly titlebarTitle?: boolean
      readonly responsiveRect?: NekoWorkspaceResponsiveRect | null
      readonly minWidth?: number
      readonly minHeight?: number
      readonly maxWidth?: number
      readonly maxHeight?: number
      readonly render?: NekoWorkspaceRender
    }
    interface NekoWorkspaceWindowSnapshot {
      readonly id: string
      readonly title: string
      /** Requested workspace-local logical geometry; layout() contains constrained published rectangles. */
      readonly rect: NekoWorkspaceRect
      readonly visible: boolean
      readonly draggable: boolean
      readonly closable: boolean
      readonly minimizable: boolean
      readonly maximizable: boolean
      readonly resizable: boolean
      readonly titlebarHeight: number
      readonly titlebarButtonWidth: number
      readonly titlebarTitle: boolean
      readonly responsiveRect: NekoWorkspaceResponsiveRect | null
      readonly minWidth: number
      readonly minHeight: number
      readonly maxWidth?: number
      readonly maxHeight?: number
      readonly mode: NekoWorkspaceWindowMode
      readonly restoreMode: 'normal' | 'maximized'
    }
    interface NekoWorkspaceSnapshot {
      readonly windows: readonly NekoWorkspaceWindowSnapshot[]
      readonly activeId: string | null
    }
    interface NekoWorkspaceMinimizedContext {
      readonly windows: readonly NekoWorkspaceWindowSnapshot[]
      restore(id: string): NekoWorkspaceResult
      activate(id: string): NekoWorkspaceResult
    }
    interface NekoWorkspaceOptions {
      readonly id?: string
      readonly viewport?: import('nekojs/jsx-runtime').NekoUiViewportInput
      readonly closeWhenEmpty?: boolean
      readonly minimizedPresentation?: 'none' | 'taskbar' | 'floating' |
        ((context: NekoWorkspaceMinimizedContext) => import('nekojs/jsx-runtime').NekoVNode | null)
      readonly taskbarHeight?: number
    }
    interface NekoWorkspaceController {
      add(id: string, render: NekoWorkspaceRender, config?: NekoWorkspaceWindowConfig): NekoWorkspaceResult
      open(): NekoWorkspaceResult
      update(id: string, patch: NekoWorkspaceWindowPatch): NekoWorkspaceResult
      closeWindow(id: string): NekoWorkspaceResult
      hide(id: string): NekoWorkspaceResult
      show(id: string): NekoWorkspaceResult
      activate(id: string): NekoWorkspaceResult
      minimize(id: string): NekoWorkspaceResult
      maximize(id: string): NekoWorkspaceResult
      restore(id: string): NekoWorkspaceResult
      resize(id: string, rect: Partial<NekoWorkspaceRect>): NekoWorkspaceResult
      close(): NekoWorkspaceResult
      isDisposed(): boolean
      snapshot(): NekoWorkspaceSnapshot
      layout(): import('nekojs/jsx-runtime').NekoUiLayoutSnapshot | null
    }
    type NekoWorkspaceFactory = (host: NekoWorkspaceHost, options?: NekoWorkspaceOptions) => NekoWorkspaceController

}
