;(function () {
  const inspectCustom = Symbol.for('nodejs.util.inspect.custom')

  function format(format: unknown, ...args: unknown[]): string {
    if (typeof format !== 'string') return [format, ...args].map(String).join(' ')
    let index = 0
    return (format as string).replace(/%[sdjifoO%]/g, token => {
      if (token === '%%') return '%'
      const value = args[index++]
      if (token === '%j') {
        try { return JSON.stringify(value) } catch { return '[Circular]' }
      }
      return String(value)
    }) + (index < args.length ? ' ' + args.slice(index).map(String).join(' ') : '')
  }

  interface NekoInspect {
    (value: unknown): string
    custom: symbol
  }
  type AnyMap = Map<unknown, unknown>
  type AnyWeakMap = WeakMap<object, unknown>

  const inspect = function inspect(value: unknown): string {
    if (value && typeof (value as any)[inspectCustom] === 'function') return String((value as any)[inspectCustom]())
    try { return JSON.stringify(value, null, 2) } catch { return String(value) }
  } as NekoInspect
  inspect.custom = inspectCustom

  function promisify(fn: Function): Function {
    return function promisified(...args: unknown[]): Promise<unknown> {
      return new Promise((resolve, reject) => fn(...args, (err, value) => { if (err) reject(err); else resolve(value) }))
    }
  }

  function callbackify(fn: Function): Function {
    return function callbackified(...args: unknown[]): void {
      const cb = args.pop() as Function
      Promise.resolve(fn(...args)).then(value => cb(null, value), err => cb(err))
    }
  }

  function deprecate<T extends Function>(fn: T, message: string, code?: string): T {
    let warned = false
    return function deprecated(...args: unknown[]): unknown {
      if (!warned) {
        warned = true
        if (typeof console !== 'undefined' && typeof console.warn === 'function') console.warn(code ? `[${code}] ${message}` : message)
      }
      return (fn as any).apply(this, args)
    } as unknown as T
  }

  function inherits(ctor: Function, superCtor: Function): void {
    if (typeof ctor !== 'function') throw new TypeError('ctor must be a function')
    if (typeof superCtor !== 'function') throw new TypeError('superCtor must be a function')
    ;(ctor as any).super_ = superCtor
    ctor.prototype = Object.create(superCtor.prototype, {
      constructor: { value: ctor, enumerable: false, writable: true, configurable: true }
    })
  }

  function debuglog(_section?: unknown): Function {
    return function noopDebuglog(): void {}
  }

  function isDeepStrictEqual(left: unknown, right: unknown): boolean {
    if (Object.is(left, right)) return true
    if (typeof left !== typeof right) return false
    if (left === null || right === null) return false
    if (typeof left !== 'object') return false
    if ((left as object).constructor !== (right as object).constructor) return false
    if (left instanceof Date) return Object.is((left as Date).getTime(), (right as Date).getTime())
    if (left instanceof RegExp) return String(left) === String(right)
    if (left instanceof Map) {
      if (left.size !== (right as Map<unknown, unknown>).size) return false
      for (const [key, value] of left) {
        if (!(right as Map<unknown, unknown>).has(key) || !isDeepStrictEqual(value, (right as Map<unknown, unknown>).get(key))) return false
      }
      return true
    }
    if (left instanceof Set) {
      if (left.size !== (right as Set<unknown>).size) return false
      for (const value of left) if (!(right as Set<unknown>).has(value)) return false
      return true
    }
    if (ArrayBuffer.isView(left)) {
      if (!ArrayBuffer.isView(right) || left.byteLength !== (right as ArrayBufferView).byteLength) return false
      const leftBytes = new Uint8Array(left.buffer, left.byteOffset, left.byteLength)
      const rightBytes = new Uint8Array((right as any).buffer, (right as any).byteOffset, (right as any).byteLength)
      for (let i = 0; i < leftBytes.length; i++) if (!Object.is(leftBytes[i], rightBytes[i])) return false
      return true
    }
    const leftKeys = Reflect.ownKeys(left as object)
    const rightKeys = Reflect.ownKeys(right as object)
    if (leftKeys.length !== rightKeys.length) return false
    for (const key of leftKeys) {
      if (!Object.prototype.hasOwnProperty.call(right, key)) return false
      if (!isDeepStrictEqual((left as any)[key], (right as any)[key])) return false
    }
    return true
  }

  function isArgumentsObject(value: unknown): value is ArrayLike<unknown> {
    return Object.prototype.toString.call(value) === '[object Arguments]'
  }

  function objectTag(value: unknown): string {
    return Object.prototype.toString.call(value)
  }

  function isBoxedPrimitive(value: unknown): value is object {
    const tag = objectTag(value)
    return tag === '[object Boolean]' || tag === '[object Number]' || tag === '[object String]' || tag === '[object Symbol]' || tag === '[object BigInt]'
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
    types: {
      isPromise: (value: unknown): value is Promise<unknown> => !!(value && typeof (value as any).then === 'function'),
      isProxy: (_value: unknown): value is object => false,
      isNativeError: (value: unknown): value is Error => value instanceof Error,
      isDate: (value: unknown): value is Date => value instanceof Date,
      isRegExp: (value: unknown): value is RegExp => value instanceof RegExp,
      isMap: (value: unknown): value is AnyMap => value instanceof Map,
      isSet: (value: unknown): value is Set<unknown> => value instanceof Set,
      isWeakMap: (value: unknown): value is AnyWeakMap => value instanceof WeakMap,
      isWeakSet: (value: unknown): value is WeakSet<object> => value instanceof WeakSet,
      isArrayBuffer: (value: unknown): value is ArrayBuffer => value instanceof ArrayBuffer,
      isSharedArrayBuffer: (value: unknown): value is SharedArrayBuffer => typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer,
      isAnyArrayBuffer: (value: unknown): value is ArrayBufferLike => value instanceof ArrayBuffer || (typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer),
      isTypedArray: (value: unknown): value is ArrayBufferView => ArrayBuffer.isView(value) && !(value instanceof DataView),
      isUint8Array: (value: unknown): value is Uint8Array => value instanceof Uint8Array,
      isDataView: (value: unknown): value is DataView => value instanceof DataView,
      isArgumentsObject,
      isBoxedPrimitive,
      isBooleanObject: (value: unknown): value is Boolean => objectTag(value) === '[object Boolean]',
      isNumberObject: (value: unknown): value is Number => objectTag(value) === '[object Number]',
      isStringObject: (value: unknown): value is String => objectTag(value) === '[object String]',
      isSymbolObject: (value: unknown): value is Symbol => objectTag(value) === '[object Symbol]',
      isBigIntObject: (value: unknown): value is BigInt => objectTag(value) === '[object BigInt]'
    }
  }

  globalThis.__nekoNodeDefine(['util', 'node:util'], util)
})()
