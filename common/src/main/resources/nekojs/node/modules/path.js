;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  const path = {
    get sep() { return runtime.path().sep() },
    get delimiter() { return runtime.path().delimiter() },
    join: (...parts) => String(runtime.path().join(...parts.map(String))),
    resolve: (...parts) => String(runtime.path().resolve(...parts.map(String))),
    normalize: value => String(runtime.path().normalize(String(value))),
    dirname: value => String(runtime.path().dirname(String(value))),
    basename: (value, suffix) => suffix === undefined ? String(runtime.path().basename(String(value))) : String(runtime.path().basename(String(value), String(suffix))),
    extname: value => String(runtime.path().extname(String(value))),
    relative: (from, to) => String(runtime.path().relative(String(from), String(to))),
    isAbsolute: value => runtime.path().isAbsolute(String(value)),
    parse: value => runtime.path().parse(String(value)),
    format: value => String(runtime.path().format(value)),
    toNamespacedPath: value => String(value),
    posix: runtime.path().posix(),
    win32: runtime.path().win32()
  }

  try {
    path.posix.toNamespacedPath = value => String(value)
    path.posix.format = value => path.format(value)
    path.posix.parse = value => path.parse(value)
    path.posix.resolve = (...parts) => path.resolve(...parts)
    path.posix.relative = (from, to) => path.relative(from, to)
    path.posix.isAbsolute = value => String(value).startsWith('/')
  } catch (_) {}
  try {
    path.win32.toNamespacedPath = value => String(value)
    path.win32.format = value => path.format(value)
    path.win32.parse = value => path.parse(value)
    path.win32.resolve = (...parts) => path.resolve(...parts)
    path.win32.relative = (from, to) => path.relative(from, to)
    path.win32.isAbsolute = value => /^[A-Za-z]:[/\\]/.test(String(value)) || String(value).startsWith('\\')
  } catch (_) {}

  globalThis.__nekoNodeDefine(['path', 'node:path'], path)
})()
