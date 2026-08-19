;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  interface NekoParsedPath { root: string; dir: string; base: string; ext: string; name: string }
  interface NekoPathApi {
    readonly sep: string
    readonly delimiter: string
    join(...parts: string[]): string
    resolve(...parts: string[]): string
    normalize(path: string): string
    dirname(path: string): string
    basename(path: string, suffix?: string): string
    extname(path: string): string
    relative(from: string, to: string): string
    isAbsolute(path: string): boolean
    parse(path: string): NekoParsedPath
    format(pathObject: unknown): string
    toNamespacedPath(path: string): string
  }
  interface NekoPathModule extends NekoPathApi {
    posix: NekoPathApi
    win32: NekoPathApi
  }

  function isWinHost(): boolean { return String(runtime.process().platform()) === 'win32' }

  // ---- 平台（宿主实现）parse/format 的 JS 适配 ----
  // 宿主 parse 返回 Java record（guest 侧成员是访问器方法），必须解包成普通对象；
  // 宿主 format 只接受 PathParts record（guest 无法构造），format 在 JS 侧实现。

  function parsePlatform(value: string): NekoParsedPath {
    const raw = runtime.path().parse(value)
    return {
      root: String(raw.root()),
      dir: String(raw.dir()),
      base: String(raw.base()),
      ext: String(raw.ext()),
      name: String(raw.name())
    }
  }

  function baseFromPathObject(value: unknown): string {
    const o = (value || {}) as Record<string, unknown>
    if (o.base !== undefined && String(o.base) !== '') return String(o.base)
    return String(o.name ?? '') + String(o.ext ?? '')
  }

  function formatPlatform(value: unknown): string {
    const o = (value || {}) as Record<string, unknown>
    const base = baseFromPathObject(o)
    const dir = String(o.dir ?? '')
    if (dir === '') return base
    const sep = String(runtime.path().sep())
    const dirStr = dir.endsWith(sep) || dir.endsWith('/') || dir.endsWith('\\') ? dir : dir + sep
    return dirStr + base
  }

  function toNamespacedPathPlatform(value: string): string {
    if (!isWinHost()) return value
    const v = value.replace(/\//g, '\\')
    if (!runtime.path().isAbsolute(v)) return v
    return '\\\\?\\' + v
  }

  // ---- posix 独立实现（不依赖宿主平台语义） ----

  function posixResolveImpl(...parts: string[]): string {
    const segments: string[] = []
    let absolute = false
    for (const part of parts.map(String)) {
      if (!part) continue
      if (part.startsWith('/')) {
        absolute = true
        segments.length = 0
      }
      for (const segment of part.split('/')) {
        if (!segment || segment === '.') continue
        if (segment === '..') {
          if (segments.length > 0 && segments[segments.length - 1] !== '..') segments.pop()
          else if (!absolute) segments.push('..')
        } else {
          segments.push(segment)
        }
      }
    }
    const joined = segments.join('/')
    if (absolute) return '/' + joined
    const cwd = String(runtime.process().cwd()).replace(/\\/g, '/').replace(/\/+$/, '')
    if (!joined) return cwd || '.'
    return (cwd ? cwd + '/' : '') + joined
  }

  function posixRelativeImpl(from: string, to: string): string {
    const fromSegs = posixResolveImpl(String(from)).split('/').filter(Boolean)
    const toSegs = posixResolveImpl(String(to)).split('/').filter(Boolean)
    let i = 0
    while (i < fromSegs.length && i < toSegs.length && fromSegs[i] === toSegs[i]) i++
    const result: string[] = []
    for (let u = i; u < fromSegs.length; u++) result.push('..')
    for (let r = i; r < toSegs.length; r++) result.push(toSegs[r])
    return result.length > 0 ? result.join('/') : '.'
  }

  function posixParseImpl(value: string): NekoParsedPath {
    const raw = String(value)
    const trimmed = raw.replace(/\/+$/, '')
    const effective = trimmed || (raw.startsWith('/') ? '/' : raw)
    const lastSlash = effective.lastIndexOf('/')
    const base = lastSlash >= 0 ? effective.slice(lastSlash + 1) : effective
    let dir: string
    if (lastSlash < 0) dir = '.'
    else if (lastSlash === 0) dir = '/'
    else dir = effective.slice(0, lastSlash)
    const root = effective.startsWith('/') ? '/' : ''
    const dot = base.lastIndexOf('.')
    const ext = dot > 0 ? base.slice(dot) : ''
    const name = ext ? base.slice(0, base.length - ext.length) : base
    return { root, dir, base, ext, name }
  }

  function posixFormatImpl(value: unknown): string {
    const o = (value || {}) as Record<string, unknown>
    const base = baseFromPathObject(o)
    const dir = String(o.dir ?? '')
    if (dir === '') return base
    if (dir === '/') return '/' + base
    return dir.replace(/\/+$/, '') + '/' + base
  }

  function createPosixApi(): NekoPathApi {
    const impl = runtime.path().posix()
    return {
      sep: '/',
      delimiter: ':',
      join(...parts): string { return String(impl.join(...parts.map(String))) },
      resolve: posixResolveImpl,
      normalize(value): string { return String(impl.normalize(String(value))) },
      dirname(value): string { return String(impl.dirname(String(value))) },
      basename(value, suffix): string {
        const base = String(impl.basename(String(value)))
        return stripSuffix(base, suffix)
      },
      extname(value): string { return String(impl.extname(String(value))) },
      relative: posixRelativeImpl,
      isAbsolute(value): boolean { return String(value).startsWith('/') },
      parse(value): NekoParsedPath { return posixParseImpl(String(value)) },
      format(value): string { return posixFormatImpl(value) },
      toNamespacedPath(value): string { return String(value) }
    }
  }

  // ---- win32 独立实现（宿主为 win32 时 resolve/relative 委托平台宿主，其余为 JS 实现） ----

  function win32ResolveImpl(...parts: string[]): string {
    let device = ''
    let absolute = false
    const segments: string[] = []
    for (const part of parts.map(String)) {
      if (!part) continue
      const unified = part.replace(/\//g, '\\')
      const drive = /^([A-Za-z]:)(.*)$/.exec(unified)
      let body = unified
      if (drive) {
        device = drive[1]
        body = drive[2]
        segments.length = 0
        absolute = false
      }
      if (body.startsWith('\\')) {
        absolute = true
        segments.length = 0
        body = body.replace(/^\\+/, '')
      }
      for (const segment of body.split('\\')) {
        if (!segment || segment === '.') continue
        if (segment === '..') {
          if (segments.length > 0 && segments[segments.length - 1] !== '..') segments.pop()
          else if (!absolute && !device) segments.push('..')
        } else {
          segments.push(segment)
        }
      }
    }
    const joined = segments.join('\\')
    let result: string
    if (device) result = device + '\\' + joined
    else if (absolute) result = '\\' + joined
    else if (joined) result = joined
    else result = '.'
    if (!absolute && !device && joined) {
      const cwd = String(runtime.process().cwd()).replace(/\//g, '\\').replace(/\\+$/, '')
      result = cwd ? cwd + '\\' + joined : joined
    }
    return result
  }

  function win32RelativeImpl(from: string, to: string): string {
    const fromResolved = win32ResolveImpl(String(from))
    const toResolved = win32ResolveImpl(String(to))
    const fromDrive = /^[A-Za-z]:/.exec(fromResolved)
    const toDrive = /^[A-Za-z]:/.exec(toResolved)
    if (fromDrive && toDrive && fromDrive[1].toLowerCase() !== toDrive[1].toLowerCase()) return toResolved
    const fromSegs = fromResolved.split('\\').filter(Boolean)
    const toSegs = toResolved.split('\\').filter(Boolean)
    let i = 0
    while (i < fromSegs.length && i < toSegs.length && fromSegs[i].toLowerCase() === toSegs[i].toLowerCase()) i++
    const result: string[] = []
    for (let u = i; u < fromSegs.length; u++) result.push('..')
    for (let r = i; r < toSegs.length; r++) result.push(toSegs[r])
    return result.length > 0 ? result.join('\\') : '.'
  }

  function win32ParseImpl(value: string): NekoParsedPath {
    const raw = String(value).replace(/\//g, '\\')
    const trimmed = raw.replace(/\\+$/, '')
    const effective = trimmed || raw
    let root = ''
    if (effective.startsWith('\\\\')) {
      const match = /^\\\\[^\\]+(\\[^\\]+)?/.exec(effective)
      root = match ? match[0] : '\\\\'
    } else {
      const drive = /^[A-Za-z]:\\/.exec(effective)
      if (drive) root = drive[0]
      else if (effective.startsWith('\\')) root = '\\'
    }
    const body = root ? effective.slice(root.length) : effective
    const base = body.split('\\').pop() || ''
    let dir: string
    if (!root && !body.includes('\\')) dir = '.'
    else {
      dir = (root + body.slice(0, body.length - base.length)).replace(/\\+$/, '')
      if (dir === '') dir = root || '.'
    }
    const dot = base.lastIndexOf('.')
    const ext = dot > 0 ? base.slice(dot) : ''
    const name = ext ? base.slice(0, base.length - ext.length) : base
    return { root, dir, base, ext, name }
  }

  function win32FormatImpl(value: unknown): string {
    const o = (value || {}) as Record<string, unknown>
    const base = baseFromPathObject(o)
    const dir = String(o.dir ?? '')
    if (dir === '') return base
    if (dir.endsWith('\\')) return dir + base
    return dir + '\\' + base
  }

  function createWin32Api(): NekoPathApi {
    const impl = runtime.path().win32()
    return {
      sep: '\\',
      delimiter: ';',
      join(...parts): string { return String(impl.join(...parts.map(String))) },
      resolve(...parts): string {
        if (isWinHost()) return String(runtime.path().resolve(...parts.map(String)))
        return win32ResolveImpl(...parts)
      },
      normalize(value): string { return String(impl.normalize(String(value))) },
      dirname(value): string { return String(impl.dirname(String(value))) },
      basename(value, suffix): string {
        const base = String(impl.basename(String(value)))
        return stripSuffix(base, suffix)
      },
      extname(value): string { return String(impl.extname(String(value))) },
      relative(from, to): string {
        if (isWinHost()) return String(runtime.path().relative(String(from), String(to)))
        return win32RelativeImpl(String(from), String(to))
      },
      isAbsolute(value): boolean { return impl.isAbsolute(String(value)) },
      parse(value): NekoParsedPath { return win32ParseImpl(String(value)) },
      format(value): string { return win32FormatImpl(value) },
      toNamespacedPath(value): string {
        const v = String(value).replace(/\//g, '\\')
        if (!impl.isAbsolute(v)) return v
        return '\\\\?\\' + v
      }
    }
  }

  function stripSuffix(base: string, suffix: string | undefined): string {
    if (suffix === undefined || suffix === '') return base
    return base.endsWith(suffix) ? base.slice(0, base.length - suffix.length) : base
  }

  const path: NekoPathModule = {
    get sep(): string { return runtime.path().sep() },
    get delimiter(): string { return runtime.path().delimiter() },
    join(...parts): string { return String(runtime.path().join(...parts.map(String))) },
    resolve(...parts): string { return String(runtime.path().resolve(...parts.map(String))) },
    normalize(value): string { return String(runtime.path().normalize(String(value))) },
    dirname(value): string { return String(runtime.path().dirname(String(value))) },
    basename(value, suffix): string {
      if (suffix === undefined) return String(runtime.path().basename(String(value)))
      return String(runtime.path().basename(String(value), String(suffix)))
    },
    extname(value): string { return String(runtime.path().extname(String(value))) },
    relative(from, to): string { return String(runtime.path().relative(String(from), String(to))) },
    isAbsolute(value): boolean { return runtime.path().isAbsolute(String(value)) },
    parse(value): NekoParsedPath { return parsePlatform(String(value)) },
    format(value): string { return formatPlatform(value) },
    toNamespacedPath(value): string { return toNamespacedPathPlatform(String(value)) },
    posix: createPosixApi(),
    win32: createWin32Api()
  }

  globalThis.__nekoNodeDefine(['path', 'node:path'], path)
})()
