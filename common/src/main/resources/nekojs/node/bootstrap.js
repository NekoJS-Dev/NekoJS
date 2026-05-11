;(function () {
  const nativeRequire = typeof require === 'function' ? require : undefined
  const modules = globalThis.__nekoNodeBuiltins

  if (nativeRequire) {
    const wrappedRequire = function (id) {
      id = String(id)
      if (Object.prototype.hasOwnProperty.call(modules, id)) return modules[id]
      return nativeRequire.apply(this, arguments)
    }
    for (const key of Object.getOwnPropertyNames(nativeRequire)) {
      try { wrappedRequire[key] = nativeRequire[key] } catch (_) {}
    }
    globalThis.require = wrappedRequire
  }
})()
