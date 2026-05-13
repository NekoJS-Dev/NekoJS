;(function () {
  const nativeRequire = typeof require === 'function' ? require : undefined
  const resolveSpecialModule = globalThis.__nekoNodeResolve
  const noModule = globalThis.__nekoNodeNoModule

  if (nativeRequire) {
    const wrappedRequire = function (id) {
      id = String(id)
      const resolved = typeof resolveSpecialModule === 'function' ? resolveSpecialModule(id) : noModule
      if (resolved !== noModule) return resolved
      return nativeRequire.apply(this, arguments)
    }
    for (const key of Object.getOwnPropertyNames(nativeRequire)) {
      try { wrappedRequire[key] = nativeRequire[key] } catch (_) {}
    }
    globalThis.require = wrappedRequire
  }
})()
