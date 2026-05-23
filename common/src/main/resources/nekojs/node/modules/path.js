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

  try { path.posix.toNamespacedPath = value => String(value) } catch (_) {}
  try { path.win32.toNamespacedPath = value => String(value) } catch (_) {}

  globalThis.__nekoNodeDefine(['path', 'node:path'], path)
})()
