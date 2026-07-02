;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  interface NekoPathModule {
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
    parse(path: string): unknown
    format(pathObject: unknown): string
    toNamespacedPath(path: string): string
    posix: NekoPathModule
    win32: NekoPathModule
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
    parse(value): unknown { return runtime.path().parse(String(value)) },
    format(value): string { return String(runtime.path().format(value)) },
    toNamespacedPath(value): string { return String(value) },
    posix: runtime.path().posix(),
    win32: runtime.path().win32()
  }

  try {
    path.posix.toNamespacedPath = (value): string => String(value)
    path.posix.format = (value): string => path.format(value)
    path.posix.parse = (value): unknown => path.parse(value)
    path.posix.resolve = (...parts): string => path.resolve(...parts)
    path.posix.relative = (from, to): string => path.relative(from, to)
    path.posix.isAbsolute = (value): boolean => { return String(value).startsWith('/') }
  } catch (_) {}
  try {
    path.win32.toNamespacedPath = (value): string => String(value)
    path.win32.format = (value): string => path.format(value)
    path.win32.parse = (value): unknown => path.parse(value)
    path.win32.resolve = (...parts): string => path.resolve(...parts)
    path.win32.relative = (from, to): string => path.relative(from, to)
    path.win32.isAbsolute = (value): boolean => {
      const v = String(value)
      const re = /^[A-Za-z]:[/\\]/
      return re.test(v) || v.startsWith('\\')
    }
  } catch (_) {}

  globalThis.__nekoNodeDefine(['path', 'node:path'], path)
})()
