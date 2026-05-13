;(function() {
  if (typeof require !== 'function') return
  if (require.__nekoNodeBuiltinPatched) return
  const nativeRequire = require
  const resolveSpecialModule = globalThis.__nekoNodeResolve
  const noModule = globalThis.__nekoNodeNoModule
  function nekoRequire(id) {
    id = String(id)
    const resolved = typeof resolveSpecialModule === 'function' ? resolveSpecialModule(id) : noModule
    if (resolved !== noModule) return resolved
    return nativeRequire.apply(this, arguments)
  }
  for (const key of Object.getOwnPropertyNames(nativeRequire)) {
    try { nekoRequire[key] = nativeRequire[key] } catch (_) {}
  }
  Object.defineProperty(nekoRequire, '__nekoNodeBuiltinPatched', { value: true })
  require = nekoRequire
})()
