;(function () {
  const inspectCustom = Symbol.for('nodejs.util.inspect.custom')

  function format(format, ...args) {
    if (typeof format !== 'string') return [format, ...args].map(String).join(' ')
    let index = 0
    return format.replace(/%[sdjifoO%]/g, token => {
      if (token === '%%') return '%'
      const value = args[index++]
      if (token === '%j') {
        try { return JSON.stringify(value) } catch { return '[Circular]' }
      }
      return String(value)
    }) + (index < args.length ? ' ' + args.slice(index).map(String).join(' ') : '')
  }

  function inspect(value) {
    if (value && typeof value[inspectCustom] === 'function') return String(value[inspectCustom]())
    try { return JSON.stringify(value, null, 2) } catch { return String(value) }
  }
  inspect.custom = inspectCustom

  function promisify(fn) {
    return (...args) => new Promise((resolve, reject) => fn(...args, (err, value) => err ? reject(err) : resolve(value)))
  }

  function callbackify(fn) {
    return (...args) => {
      const cb = args.pop()
      Promise.resolve(fn(...args)).then(value => cb(null, value), err => cb(err))
    }
  }

  function deprecate(fn, message, code) {
    let warned = false
    return function deprecated(...args) {
      if (!warned) {
        warned = true
        if (typeof console !== 'undefined' && typeof console.warn === 'function') console.warn(code ? `[${code}] ${message}` : message)
      }
      return fn.apply(this, args)
    }
  }

  function inherits(ctor, superCtor) {
    if (typeof ctor !== 'function') throw new TypeError('ctor must be a function')
    if (typeof superCtor !== 'function') throw new TypeError('superCtor must be a function')
    ctor.super_ = superCtor
    ctor.prototype = Object.create(superCtor.prototype, {
      constructor: { value: ctor, enumerable: false, writable: true, configurable: true }
    })
  }

  function debuglog() {
    return function noopDebuglog() {}
  }

  function isDeepStrictEqual(left, right) {
    if (Object.is(left, right)) return true
    if (typeof left !== typeof right) return false
    if (left === null || right === null) return false
    if (typeof left !== 'object') return false
    if (left.constructor !== right.constructor) return false
    if (left instanceof Date) return Object.is(left.getTime(), right.getTime())
    if (left instanceof RegExp) return String(left) === String(right)
    if (left instanceof Map) {
      if (left.size !== right.size) return false
      for (const [key, value] of left) {
        if (!right.has(key) || !isDeepStrictEqual(value, right.get(key))) return false
      }
      return true
    }
    if (left instanceof Set) {
      if (left.size !== right.size) return false
      for (const value of left) if (!right.has(value)) return false
      return true
    }
    if (ArrayBuffer.isView(left)) {
      if (!ArrayBuffer.isView(right) || left.byteLength !== right.byteLength) return false
      const leftBytes = new Uint8Array(left.buffer, left.byteOffset, left.byteLength)
      const rightBytes = new Uint8Array(right.buffer, right.byteOffset, right.byteLength)
      for (let i = 0; i < leftBytes.length; i++) if (!Object.is(leftBytes[i], rightBytes[i])) return false
      return true
    }
    const leftKeys = Reflect.ownKeys(left)
    const rightKeys = Reflect.ownKeys(right)
    if (leftKeys.length !== rightKeys.length) return false
    for (const key of leftKeys) {
      if (!Object.prototype.hasOwnProperty.call(right, key)) return false
      if (!isDeepStrictEqual(left[key], right[key])) return false
    }
    return true
  }

  function isArgumentsObject(value) {
    return Object.prototype.toString.call(value) === '[object Arguments]'
  }

  function objectTag(value) {
    return Object.prototype.toString.call(value)
  }

  function isBoxedPrimitive(value) {
    const tag = objectTag(value)
    return tag === '[object Boolean]' || tag === '[object Number]' || tag === '[object String]' || tag === '[object Symbol]' || tag === '[object BigInt]'
  }

  const types = {
    isPromise: value => !!(value && typeof value.then === 'function'),
    isProxy: () => false,
    isNativeError: value => value instanceof Error,
    isDate: value => value instanceof Date,
    isRegExp: value => value instanceof RegExp,
    isMap: value => value instanceof Map,
    isSet: value => value instanceof Set,
    isWeakMap: value => value instanceof WeakMap,
    isWeakSet: value => value instanceof WeakSet,
    isArrayBuffer: value => value instanceof ArrayBuffer,
    isSharedArrayBuffer: value => typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer,
    isAnyArrayBuffer: value => value instanceof ArrayBuffer || (typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer),
    isTypedArray: value => ArrayBuffer.isView(value) && !(value instanceof DataView),
    isUint8Array: value => value instanceof Uint8Array,
    isDataView: value => value instanceof DataView,
    isArgumentsObject,
    isBoxedPrimitive,
    isBooleanObject: value => objectTag(value) === '[object Boolean]',
    isNumberObject: value => objectTag(value) === '[object Number]',
    isStringObject: value => objectTag(value) === '[object String]',
    isSymbolObject: value => objectTag(value) === '[object Symbol]',
    isBigIntObject: value => objectTag(value) === '[object BigInt]'
  }

  const util = {
    format,
    inspect,
    promisify,
    callbackify,
    deprecate,
    inherits,
    isDeepStrictEqual,
    debuglog,
    types
  }

  globalThis.__nekoNodeDefine(['util', 'node:util'], util)
})()
