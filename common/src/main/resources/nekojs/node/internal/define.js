;(function () {
  const runtime = globalThis.__nekoNodeRuntime
  const modules = globalThis.__nekoNodeBuiltins || Object.create(null)
  const NO_MODULE = globalThis.__nekoNodeNoModule || Symbol('neko.node.no-module')
  const javaNamespaceCache = new Map()
  const javaClassModuleCache = new Map()
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

  function normalizeJsxChildren(children) {
    const normalized = []
    for (const child of children) {
      if (Array.isArray(child)) {
        normalized.push(...normalizeJsxChildren(child))
      } else if (child !== null && child !== undefined && child !== false && child !== true) {
        normalized.push(child)
      }
    }
    return normalized
  }

  function createJsxElement(type, props, ...children) {
    const normalizedChildren = normalizeJsxChildren(children)
    const normalizedProps = props == null ? {} : { ...props }
    if (normalizedChildren.length === 1) {
      normalizedProps.children = normalizedChildren[0]
    } else if (normalizedChildren.length > 1) {
      normalizedProps.children = normalizedChildren
    }
    return { tag: type, props: normalizedProps, children: normalizedChildren }
  }

  function createJsxFragment(...children) {
    return createJsxElement(Symbol.for('nekojs.jsx.fragment'), null, ...children)
  }

  globalThis.__nekoJsxFactory = globalThis.__nekoJsxFactory || createJsxElement
  globalThis.__nekoJsxFragment = globalThis.__nekoJsxFragment || createJsxFragment

  function normalizeJavaSpecifier(id) {
    if (typeof id !== 'string') return undefined
    const raw = id.startsWith('java:') ? id.slice(5) : id
    const body = raw.trim().replace(/\\/g, '/')
    if (!body || body.startsWith('.') || body.startsWith('/') || body.endsWith('/') || body.includes('..')) return undefined
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*(?:[./][A-Za-z_$][A-Za-z0-9_$]*)*$/.test(body)) return undefined
    return body.replace(/\//g, '.')
  }

  function isJavaSpecifier(id) {
    if (typeof id !== 'string') return false
    return id.startsWith('java:') || id.startsWith('java.') || id.startsWith('java/')
  }

  function isJavaPackageName(name) {
    return name.split('.').every(segment => /^[a-z_]/.test(segment))
  }

  function javaClassSimpleName(className) {
    const index = className.lastIndexOf('.')
    return index < 0 ? className : className.slice(index + 1)
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

  function createJavaClassModule(className) {
    const cached = javaClassModuleCache.get(className)
    if (cached) return cached

    const type = loadJavaType(className)
    const simpleName = javaClassSimpleName(className)
    const prefixedName = `$${simpleName}`
    const module = new Proxy(type, {
      get(target, prop, receiver) {
        if (prop === '__esModule') return true
        if (prop === 'default' || prop === prefixedName) return target
        if (prop === 'then') return undefined
        if (prop === Symbol.toStringTag) return 'NekoJavaClassModule'
        if (prop === 'toString') return () => `[object NekoJavaClassModule ${className}]`
        return Reflect.get(target, prop, receiver)
      },
      has(target, prop) {
        return prop === '__esModule' || prop === 'default' || prop === prefixedName || Reflect.has(target, prop)
      }
    })

    javaClassModuleCache.set(className, module)
    return module
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

        let qualifiedName
        if (prop.startsWith('$') && prop.length > 1) {
          qualifiedName = `${prefix}.${prop.slice(1)}`
        } else {
          qualifiedName = prefix ? `${prefix}.${prop}` : prop
        }
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
    if (!isJavaSpecifier(id)) return NO_MODULE

    const target = normalizeJavaSpecifier(id)
    if (!target) {
      throw new TypeError(`Invalid Java module specifier: ${id}`)
    }

    if (isJavaPackageName(target)) {
      return createJavaNamespace(target)
    }
    return createJavaClassModule(target)
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
