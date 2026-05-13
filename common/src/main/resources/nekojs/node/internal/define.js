;(function () {
  const runtime = globalThis.__nekoNodeRuntime
  const modules = globalThis.__nekoNodeBuiltins || Object.create(null)
  const NO_MODULE = globalThis.__nekoNodeNoModule || Symbol('neko.node.no-module')
  const javaNamespaceCache = new Map()
  const javaTypeCache = new Map()

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

  function isPackageSpecifier(target) {
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/.test(target)) return false
    const segments = target.split('.')
    return segments.every(segment => /^[a-z_]/.test(segment))
  }

  function loadJavaType(className) {
    if (javaTypeCache.has(className)) {
      return javaTypeCache.get(className)
    }
    if (!globalThis.Java || typeof globalThis.Java.type !== 'function') {
      throw new Error('Java.type is unavailable in this script context.')
    }
    const type = globalThis.Java.type(className)
    javaTypeCache.set(className, type)
    return type
  }

  function createJavaNamespace(prefix) {
    const cached = javaNamespaceCache.get(prefix)
    if (cached) return cached

    const target = Object.create(null)
    const namespace = new Proxy(target, {
      get(cache, prop, receiver) {
        if (prop === '__esModule') return true
        if (prop === 'default') return receiver
        if (prop === 'then' || prop === 'constructor' || prop === '__proto__' || prop === 'prototype') return undefined
        if (prop === Symbol.toStringTag) return 'NekoJavaModule'
        if (prop === 'toString') return () => `[object NekoJavaModule ${prefix}]`
        if (typeof prop !== 'string') return Reflect.get(cache, prop, receiver)
        if (Object.prototype.hasOwnProperty.call(cache, prop)) return cache[prop]

        const qualifiedName = prefix ? `${prefix}.${prop}` : prop
        try {
          const type = loadJavaType(qualifiedName)
          cache[prop] = type
          return type
        } catch (error) {
          if (/^[a-z_][A-Za-z0-9_]*$/.test(prop)) {
            const nested = createJavaNamespace(qualifiedName)
            cache[prop] = nested
            return nested
          }
          throw error
        }
      },
      has(cache, prop) {
        return prop === '__esModule' || prop === 'default' || Object.prototype.hasOwnProperty.call(cache, prop)
      },
      getOwnPropertyDescriptor(cache, prop) {
        if (prop === '__esModule') {
          return { configurable: true, enumerable: false, value: true }
        }
        if (prop === 'default') {
          return { configurable: true, enumerable: false, value: namespace }
        }
        return Object.getOwnPropertyDescriptor(cache, prop)
      }
    })

    javaNamespaceCache.set(prefix, namespace)
    return namespace
  }

  function resolveJavaModule(id) {
    if (typeof id !== 'string' || !id.startsWith('java:')) {
      return NO_MODULE
    }

    const target = id.slice(5).trim()
    if (!target) {
      throw new TypeError(`Invalid java: module specifier: ${id}`)
    }

    if (!isPackageSpecifier(target)) {
      throw new TypeError(`Invalid java: package module specifier: ${id}. Use package-level modules like java:java.lang and named imports such as import { Integer } from 'java:java.lang'.`)
    }
    return createJavaNamespace(target)
  }

  function resolveSpecialModule(id) {
    if (Object.prototype.hasOwnProperty.call(modules, id)) return modules[id]
    return resolveJavaModule(id)
  }

  globalThis.__nekoNodeBuiltins = modules
  globalThis.__nekoNodeDefine = define
  globalThis.__nekoNodeNoModule = NO_MODULE
  globalThis.__nekoNodeResolve = resolveSpecialModule
  globalThis.__nekoNodeInternal = {
    runtime,
    modules,
    NO_MODULE,
    asError,
    callbackResult,
    promiseResult,
    encodingFromOptions,
    recursiveFromOptions,
    forceFromOptions,
    resolveSpecialModule,
    resolveJavaModule
  }
})()
