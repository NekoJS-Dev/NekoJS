;(function() {
  if (typeof require !== 'function' || !globalThis.__nekoNodeBuiltins) return
  if (require.__nekoNodeBuiltinPatched) return
  const nativeRequire = require
  const builtins = globalThis.__nekoNodeBuiltins
  function nekoRequire(id) {
    id = String(id)
    if (Object.prototype.hasOwnProperty.call(builtins, id)) return builtins[id]
    return nativeRequire.apply(this, arguments)
  }
  for (const key of Object.getOwnPropertyNames(nativeRequire)) {
    try { nekoRequire[key] = nativeRequire[key] } catch (_) {}
  }
  Object.defineProperty(nekoRequire, '__nekoNodeBuiltinPatched', { value: true })
  require = nekoRequire
})()
