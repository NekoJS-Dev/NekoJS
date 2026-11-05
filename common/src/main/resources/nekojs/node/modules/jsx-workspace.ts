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

;(function () {
  const UI = globalThis.__nekoNodeBuiltins['nekojs/jsx-runtime'].UI
  const EMPTY = Object.freeze({windows: Object.freeze([]), activeId: null})

  function plainObject(value) {
    return value != null && typeof value === 'object' && !Array.isArray(value)
      && (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null)
  }

  function validId(id) {
    if (typeof id !== 'string' || id.length === 0) throw new TypeError('[NEKO-7013] Workspace window id must be a non-empty string')
  }

  function responsiveRectPolicy(value) {
    if (value === null) return null
    if (!plainObject(value)) throw new TypeError('[NEKO-7013] Workspace responsiveRect must be a plain object or null')
    const result = {}
    for (const axis of Object.keys(value)) {
      if (!['x', 'y', 'width', 'height'].includes(axis)) throw new TypeError('[NEKO-7013] Unsupported responsiveRect field: ' + axis)
      const scalar = item => {
        if (typeof item === 'number' && Number.isFinite(item) && (axis === 'x' || axis === 'y' || item >= 0)) return item
        if (typeof item === 'string' && /^(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)%$/.test(item) && Number.isFinite(Number(item.slice(0, -1)))) return item
        throw new TypeError('[NEKO-7013] Workspace responsiveRect values must be finite numbers or percentages')
      }
      const field = value[axis]
      if (!plainObject(field)) { result[axis] = scalar(field); continue }
      const copy = {}
      if (!Object.keys(field).length) throw new TypeError('[NEKO-7013] Workspace responsiveRect profile must define base or profiles')
      for (const key of Object.keys(field)) {
        if (key === 'base') copy.base = scalar(field.base)
        else if (key === 'profiles' && plainObject(field.profiles)) {
          const profiles = {}
          for (const profile of Object.keys(field.profiles)) {
            if (!/^[1-6]$/.test(profile)) throw new RangeError('[NEKO-7013] Workspace responsiveRect profile must be between 1 and 6')
            profiles[profile] = scalar(field.profiles[profile])
          }
          copy.profiles = Object.freeze(profiles)
        } else throw new TypeError('[NEKO-7013] Unsupported workspace responsiveRect profile field: ' + key)
      }
      result[axis] = Object.freeze(copy)
    }
    return Object.freeze(result)
  }

  function windowEntry(id, render, config, previous?) {
    if (!plainObject(config)) throw new TypeError('[NEKO-7013] Workspace window configuration must be a plain object')
    for (const key of Object.keys(config)) {
      if (!['title', 'rect', 'visible', 'draggable', 'closable', 'minimizable', 'maximizable', 'resizable', 'titlebarHeight', 'titlebarButtonWidth', 'titlebarTitle', 'responsiveRect', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight', 'render'].includes(key)) {
        throw new TypeError('[NEKO-7013] Unsupported workspace window configuration: ' + key)
      }
    }
    if (typeof render !== 'function') throw new TypeError('[NEKO-7013] Workspace window render must be a function')
    const base = previous || {title: id, rect: {x: 16, y: 16, width: 240, height: 160}, visible: true, draggable: true, closable: true,
      minimizable: false, maximizable: false, resizable: false, titlebarHeight: 20, titlebarButtonWidth: 20, titlebarTitle: true,
      responsiveRect: null, minWidth: 40, minHeight: 20,
      maxWidth: undefined, maxHeight: undefined, mode: 'normal', restoreMode: 'normal'}
    if (config.rect !== undefined && !plainObject(config.rect)) throw new TypeError('[NEKO-7013] Workspace rect must be a plain object')
    const rect = {...base.rect, ...config.rect}
    for (const key of Object.keys(rect)) {
      if (!['x', 'y', 'width', 'height'].includes(key) || typeof rect[key] !== 'number' || !Number.isFinite(rect[key])) {
        throw new TypeError('[NEKO-7013] Workspace rect must contain finite x, y, width and height')
      }
    }
    if (rect.width < 40 || rect.height < 20) throw new RangeError('[NEKO-7013] Workspace window width must be at least 40 and height at least 20')
    const entry = {id, render, title: config.title === undefined ? base.title : config.title,
      rect: Object.freeze(rect), visible: config.visible === undefined ? base.visible : config.visible,
      draggable: config.draggable === undefined ? base.draggable : config.draggable,
      closable: config.closable === undefined ? base.closable : config.closable,
      minimizable: config.minimizable === undefined ? base.minimizable : config.minimizable,
      maximizable: config.maximizable === undefined ? base.maximizable : config.maximizable,
      resizable: config.resizable === undefined ? base.resizable : config.resizable,
      titlebarHeight: config.titlebarHeight === undefined ? base.titlebarHeight : config.titlebarHeight,
      titlebarButtonWidth: config.titlebarButtonWidth === undefined ? base.titlebarButtonWidth : config.titlebarButtonWidth,
      titlebarTitle: config.titlebarTitle === undefined ? base.titlebarTitle : config.titlebarTitle,
      responsiveRect: config.responsiveRect === undefined ? base.responsiveRect : responsiveRectPolicy(config.responsiveRect),
      minWidth: config.minWidth === undefined ? base.minWidth : config.minWidth,
      minHeight: config.minHeight === undefined ? base.minHeight : config.minHeight,
      maxWidth: config.maxWidth === undefined ? base.maxWidth : config.maxWidth,
      maxHeight: config.maxHeight === undefined ? base.maxHeight : config.maxHeight,
      mode: base.mode, restoreMode: base.restoreMode}
    if (typeof entry.title !== 'string') throw new TypeError('[NEKO-7013] Workspace title must be a string')
    for (const key of ['visible', 'draggable', 'closable', 'minimizable', 'maximizable', 'resizable', 'titlebarTitle']) {
      if (typeof entry[key] !== 'boolean') throw new TypeError('[NEKO-7013] Workspace ' + key + ' must be a boolean')
    }
    if (!Number.isInteger(entry.titlebarHeight) || entry.titlebarHeight < 0
      || !Number.isInteger(entry.titlebarButtonWidth) || entry.titlebarButtonWidth < 1) {
      throw new RangeError('[NEKO-7013] Workspace titlebarHeight must be a non-negative integer and titlebarButtonWidth a positive integer')
    }
    for (const axis of ['Width', 'Height']) {
      const minimum = entry['min' + axis]
      const maximum = entry['max' + axis]
      if (typeof minimum !== 'number' || !Number.isFinite(minimum) || minimum < (axis === 'Width' ? 40 : 20)
        || maximum !== undefined && (typeof maximum !== 'number' || !Number.isFinite(maximum) || maximum < minimum)) {
        throw new RangeError('[NEKO-7013] Workspace size constraints must be finite and ordered above the normal minimum')
      }
    }
    return Object.freeze(entry)
  }

  function windowSnapshot(window) {
    const snapshot = {id: window.id, title: window.title, rect: window.rect, visible: window.visible,
      draggable: window.draggable, closable: window.closable, minimizable: window.minimizable, maximizable: window.maximizable,
      mode: window.mode, restoreMode: window.restoreMode, resizable: window.resizable, responsiveRect: window.responsiveRect,
      titlebarHeight: window.titlebarHeight, titlebarButtonWidth: window.titlebarButtonWidth, titlebarTitle: window.titlebarTitle,
      minWidth: window.minWidth, minHeight: window.minHeight}
    if (window.maxWidth !== undefined) snapshot.maxWidth = window.maxWidth
    if (window.maxHeight !== undefined) snapshot.maxHeight = window.maxHeight
    return Object.freeze(snapshot)
  }

  function state(windows, activeId) {
    return Object.freeze({windows: Object.freeze(windows), activeId})
  }

  function lastVisible(windows) {
    for (let index = windows.length - 1; index >= 0; index--) if (windows[index].visible && windows[index].mode !== 'minimized') return windows[index].id
    return null
  }

  const createWorkspace: NekoWorkspaceFactory = function (host, options = {}) {
    if (host == null || typeof host.bindRoot !== 'function' || typeof host.open !== 'function' || typeof host.close !== 'function') {
      throw new TypeError('[NEKO-7013] Workspace requires a managed UI host with bindRoot, open and close')
    }
    if (!plainObject(options)) throw new TypeError('[NEKO-7013] Workspace options must be a plain object')
    if (options.closeWhenEmpty !== undefined && typeof options.closeWhenEmpty !== 'boolean') {
      throw new TypeError('[NEKO-7013] Workspace closeWhenEmpty must be a boolean')
    }
    const closeWhenEmpty = options.closeWhenEmpty === true
    const presentation = options.minimizedPresentation === undefined ? 'none' : options.minimizedPresentation
    if (presentation !== 'none' && presentation !== 'taskbar' && presentation !== 'floating' && typeof presentation !== 'function') {
      throw new TypeError('[NEKO-7013] Workspace minimizedPresentation must be none, taskbar, floating or a callback')
    }
    const taskbarHeight = options.taskbarHeight === undefined ? 28 : options.taskbarHeight
    if (!Number.isInteger(taskbarHeight) || taskbarHeight < 1) {
      throw new RangeError('[NEKO-7013] Workspace taskbarHeight must be a positive integer')
    }
    let committed = EMPTY
    let candidate = null
    let rendering = false
    let root = null

    function disposed() {
      if (!root.isDisposed()) return false
      committed = EMPTY
      candidate = null
      return true
    }

    function owner(action) {
      if (!host.isOwnerThread()) return host.enqueue(() => owner(action)) === false ? false : 'queued'
      if (root !== null && disposed()) return false
      if (rendering || root === null || root.isUpdating()) throw new Error('[NEKO-7013] Workspace cannot be changed during root publication or window rendering')
      return action()
    }

    function publish(next) {
      // The renderer can observe a tentative snapshot only during this refresh. A rejected
      // transaction must never leak into a later signal-driven render of the committed root.
      candidate = next
      try {
        if (root.refresh() !== true) return false
        committed = next
        return true
      } finally {
        candidate = null
      }
    }

    function removeWindow(id) {
      validId(id)
      if (!committed.windows.some(window => window.id === id)) return false
      const windows = committed.windows.filter(window => window.id !== id)
      const changed = publish(state(windows, committed.activeId === id ? lastVisible(windows) : committed.activeId))
      if (changed && windows.length === 0 && closeWhenEmpty) host.close()
      return changed
    }

    function activateWindow(id) {
      validId(id)
      const previous = committed.windows.find(window => window.id === id)
      if (!previous || !previous.visible) return false
      const window = previous.mode === 'minimized' ? Object.freeze({...previous, mode: previous.restoreMode}) : previous
      if (committed.activeId === id && committed.windows[committed.windows.length - 1] === window) return true
      return publish(state([...committed.windows.filter(entry => entry !== previous), window], id))
    }

    function maximizeWindow(id) {
      validId(id)
      const previous = committed.windows.find(window => window.id === id)
      if (!previous || !previous.maximizable) return false
      if (previous.mode === 'maximized') return true
      const entry = Object.freeze({...previous, mode: 'maximized'})
      const windows = committed.windows.filter(window => window !== previous)
      windows.push(entry)
      return publish(state(windows, entry.visible ? id : committed.activeId))
    }

    function minimizeWindow(id) {
      validId(id)
      const previous = committed.windows.find(window => window.id === id)
      if (!previous || !previous.minimizable) return false
      if (previous.mode === 'minimized') return true
      const entry = Object.freeze({...previous, mode: 'minimized', restoreMode: previous.mode})
      const windows = committed.windows.map(window => window === previous ? entry : window)
      return publish(state(windows, committed.activeId === id ? lastVisible(windows) : committed.activeId))
    }

    function restoreWindow(id) {
      validId(id)
      const previous = committed.windows.find(window => window.id === id)
      if (!previous) return false
      if (previous.mode === 'normal') return true
      const entry = Object.freeze({...previous, mode: previous.mode === 'minimized' ? previous.restoreMode : 'normal'})
      const windows = committed.windows.filter(window => window !== previous)
      windows.push(entry)
      return publish(state(windows, entry.visible ? id : committed.activeId))
    }

    function shownRect(window) {
      if (!window.visible) return window.rect
      function find(nodes) {
        for (const node of nodes) {
          if (node.type === 'workspace') {
            const shown = node.children.find(child => child.type === 'window' && child.id === window.id)
            if (shown) return {x: shown.rect.x - node.rect.x, y: shown.rect.y - node.rect.y,
              width: shown.rect.width, height: shown.rect.height}
          }
          const nested = find(node.children)
          if (nested) return nested
        }
        return null
      }
      return find(root.layout()?.nodes || []) || window.rect
    }

    function resizeWindow(id, patch, edge?) {
      validId(id)
      const previous = committed.windows.find(window => window.id === id)
      if (!previous || !previous.resizable || previous.mode !== 'normal') return false
      if (!plainObject(patch)) throw new TypeError('[NEKO-7013] Workspace resize must be a plain rectangle patch')
      for (const key of Object.keys(patch)) {
        if (!['x', 'y', 'width', 'height'].includes(key) || typeof patch[key] !== 'number' || !Number.isFinite(patch[key])
          || (key === 'width' || key === 'height') && patch[key] < 0) {
          throw new TypeError('[NEKO-7013] Workspace resize requires finite geometry and non-negative sizes')
        }
      }
      if (edge !== undefined && !['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'].includes(edge)) {
        throw new TypeError('[NEKO-7013] Workspace resize edge is invalid')
      }
      const shown = shownRect(previous)
      const rect = {...shown, ...patch}
      rect.width = Math.max(previous.minWidth, Math.min(previous.maxWidth ?? Infinity, rect.width))
      rect.height = Math.max(previous.minHeight, Math.min(previous.maxHeight ?? Infinity, rect.height))
      // Native proposals retain the captured opposite edge across controlled publications.
      if (edge?.includes('w')) rect.x = patch.x + patch.width - rect.width
      if (edge?.includes('n')) rect.y = patch.y + patch.height - rect.height
      return updateWindow(id, {rect, responsiveRect: null})
    }

    function Content(props) {
      rendering = true
      try { return props.render() } finally { rendering = false }
    }

    function renderWorkspace() {
      const snapshot = candidate || committed
      const desktop = UI.element('workspace', {width: 'fill', height: 'fill', children: snapshot.windows.map(window => {
        const props = {id: window.id, key: window.id, title: window.title, ...window.rect,
          visible: window.visible && window.mode !== 'minimized', active: snapshot.activeId === window.id,
          draggable: window.draggable, closable: window.closable, resizable: window.resizable,
          minimizable: window.minimizable, maximizable: window.maximizable,
          mode: window.mode === 'maximized' ? 'maximized' : 'normal',
          titlebarHeight: window.titlebarHeight, titlebarButtonWidth: window.titlebarButtonWidth, titlebarTitle: window.titlebarTitle,
          minWidth: window.minWidth, minHeight: window.minHeight,
          onActivate: () => controller.activate(window.id),
          onMinimize: () => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            return current && current.visible && committed.activeId === window.id ? minimizeWindow(window.id) : false
          }),
          onMaximize: () => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            return current && current.visible && committed.activeId === window.id && current.mode === 'normal'
              ? maximizeWindow(window.id) : false
          }),
          onRestore: () => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            return current && current.visible && committed.activeId === window.id && current.mode === 'maximized'
              ? restoreWindow(window.id) : false
          }),
          onResize: event => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            if (!current || !current.visible || committed.activeId !== window.id) return false
            return resizeWindow(window.id, {x: event.x, y: event.y, width: event.width, height: event.height}, event.edge)
          }),
          onMove: event => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            if (!current || !current.visible || !current.draggable || current.mode !== 'normal' || committed.activeId !== window.id) return false
            const shown = shownRect(current)
            return updateWindow(window.id, {rect: {...shown, x: event.x, y: event.y,
              width: Math.max(40, shown.width), height: Math.max(20, shown.height)}, responsiveRect: null})
          }),
          onDismiss: () => owner(() => {
            const current = committed.windows.find(entry => entry.id === window.id)
            return current && current.visible && current.closable ? removeWindow(window.id) : false
          }),
          children: UI.element(Content, {key: 'content', render: window.render})}
        if (window.responsiveRect !== null) props.responsiveRect = window.responsiveRect
        if (window.maxWidth !== undefined) props.maxWidth = window.maxWidth
        if (window.maxHeight !== undefined) props.maxHeight = window.maxHeight
        return UI.element('window', props)
      })})
      if (presentation === 'none') return desktop
      const entries = snapshot.windows.filter(window => window.visible && window.mode === 'minimized')
      if (typeof presentation === 'function') {
        const context = Object.freeze({windows: Object.freeze(entries.map(windowSnapshot)),
          restore: controller.restore, activate: controller.activate})
        const panel = presentation(context)
        const children = [desktop]
        if (panel !== null) {
          if (!plainObject(panel) || panel.$$nekoJsx !== true || panel.type !== 'panel'
            || !plainObject(panel.props) || !Array.isArray(panel.children)) {
            throw new TypeError('[NEKO-7013] Workspace minimized presentation must return a direct panel VNode or null')
          }
          if (panel.props.width == null || panel.props.height == null || panel.props.anchor == null) {
            throw new TypeError('[NEKO-7013] Workspace minimized presentation panel requires explicit width, height and anchor')
          }
          children.push(UI.element('panel', {...panel.props, children: panel.children, workspacePresentation: true}, panel.key))
        }
        return UI.element('screen', {width: 'fill', height: 'fill', layout: 'stack', children})
      }
      const buttons = entries.map(window => {
          let suffix = 0
          let id = JSON.stringify(['nekojs.workspace.restore', root.id, window.id, suffix])
          while (snapshot.windows.some(entry => entry.id === id)) {
            id = JSON.stringify(['nekojs.workspace.restore', root.id, window.id, ++suffix])
          }
        return UI.element('button', {id, key: window.id, width: 'fill', height: presentation === 'taskbar' ? 'fill' : 20,
          text: window.title, tooltip: window.title, onClick: () => controller.restore(window.id)})
      })
      if (presentation === 'floating') {
        const children = [desktop]
        if (buttons.length > 0) {
          children.push(UI.element('panel', {key: 'minimized', width: 'fill', height: 'fill',
            maxWidth: 220, maxHeight: Math.min(160, 24 * buttons.length + 4),
            anchor: 'bottomLeft', padding: 4, workspacePresentation: true,
            children: UI.element('scroll', {width: 'fill', height: 'fill', scrollY: true,
              children: UI.element('column', {width: 'fill', gap: 4, children: buttons})})}))
        }
        return UI.element('screen', {width: 'fill', height: 'fill', layout: 'stack', children})
      }
      const bar = UI.element('panel', {key: 'minimized', width: 'fill', height: taskbarHeight, workspacePresentation: true,
        children: UI.element('row', {width: 'fill', height: 'fill', children: buttons})})
      return UI.element('screen', {width: 'fill', height: 'fill', children: [desktop, bar]})
    }

    function updateWindow(id, patch) {
      validId(id)
      if (!plainObject(patch)) throw new TypeError('[NEKO-7013] Workspace window patch must be a plain object')
      const previous = committed.windows.find(window => window.id === id)
      if (!previous) return false
      const entry = windowEntry(id, patch.render === undefined ? previous.render : patch.render, patch, previous)
      const windows = committed.windows.map(window => window === previous ? entry : window)
      const activeId = !entry.visible && committed.activeId === id ? lastVisible(windows)
        : committed.activeId === null && entry.visible && entry.mode !== 'minimized' ? id : committed.activeId
      return publish(state(windows, activeId))
    }

    const controller: NekoWorkspaceController = Object.freeze({
      add: (id, render, config = {}) => owner(() => {
        validId(id)
        if (committed.windows.some(window => window.id === id)) throw new Error('[NEKO-7013] Duplicate workspace window id: ' + id)
        const window = windowEntry(id, render, config)
        return publish(state([...committed.windows, window], window.visible ? id : committed.activeId))
      }),
      open: () => owner(() => {host.open(); return true}),
      update: (id, patch) => owner(() => updateWindow(id, patch)),
      closeWindow: id => owner(() => removeWindow(id)),
      hide: id => owner(() => updateWindow(id, {visible: false})),
      show: id => owner(() => {
        validId(id)
        const previous = committed.windows.find(window => window.id === id)
        if (!previous) return false
        const visible = windowEntry(id, previous.render, {visible: true}, previous)
        const window = visible.mode === 'minimized' ? Object.freeze({...visible, mode: visible.restoreMode}) : visible
        return publish(state([...committed.windows.filter(entry => entry !== previous), window], id))
      }),
      activate: id => owner(() => activateWindow(id)),
      minimize: id => owner(() => minimizeWindow(id)),
      maximize: id => owner(() => maximizeWindow(id)),
      restore: id => owner(() => restoreWindow(id)),
      resize: (id, rect) => owner(() => resizeWindow(id, rect)),
      close: () => owner(() => {
        if (host.close() !== true) return false
        committed = EMPTY
        candidate = null
        return true
      }),
      isDisposed: () => disposed(),
      layout: () => disposed() ? null : root.layout(),
      snapshot: () => {
        if (disposed()) return EMPTY
        return Object.freeze({activeId: committed.activeId, windows: Object.freeze(committed.windows.map(windowSnapshot))})
      }
    })
    root = UI.createRoot(renderWorkspace, host, options)
    host.bindRoot(root)
    return controller
  }

  globalThis.__nekoNodeDefine(['nekojs/jsx-workspace'], {createWorkspace: createWorkspace})
})()
