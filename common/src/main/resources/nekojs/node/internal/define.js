;(function () {
  const runtime = globalThis.__nekoNodeRuntime
  const modules = globalThis.__nekoNodeBuiltins || Object.create(null)

  function asError(error) {
    if (error instanceof Error) return error
    const message = error && error.message ? String(error.message) : String(error)
    const wrapped = new Error(message)
    wrapped.cause = error
    return wrapped
  }

  function callbackResult(callback, fn) {
    try {
      const value = fn()
      if (typeof callback === 'function') callback(null, value)
    } catch (error) {
      if (typeof callback === 'function') callback(asError(error))
    }
  }

  function promiseResult(fn) {
    try {
      return Promise.resolve(fn())
    } catch (error) {
      return Promise.reject(asError(error))
    }
  }

  function encodingFromOptions(options) {
    if (typeof options === 'string') return options
    if (options && typeof options.encoding === 'string') return options.encoding
    return undefined
  }

  function recursiveFromOptions(options) {
    return !!(options && options.recursive)
  }

  function forceFromOptions(options) {
    return !!(options && options.force)
  }

  function define(names, value) {
    for (const name of Array.isArray(names) ? names : [names]) {
      modules[name] = value
    }
    return value
  }

  globalThis.__nekoNodeBuiltins = modules
  globalThis.__nekoNodeDefine = define
  globalThis.__nekoNodeInternal = {
    runtime,
    modules,
    asError,
    callbackResult,
    promiseResult,
    encodingFromOptions,
    recursiveFromOptions,
    forceFromOptions
  }
})()
