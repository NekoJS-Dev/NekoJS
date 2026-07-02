;(function () {
  interface NekoAssertOptions {
    message?: string
    actual?: unknown
    expected?: unknown
    operator?: string
  }

  interface NekoAssertErrorOptions extends NekoAssertOptions {}

  type NekoAssertExpected = RegExp | (new (...args: unknown[]) => Error) | ((error: unknown) => boolean) | Record<string, unknown>
  type NekoSyncFn = () => unknown
  type NekoMaybeAsyncFn = (() => unknown) | Promise<unknown>
  type NekoErrorCtor = new (...args: unknown[]) => Error
  type NekoErrorPredicate = (error: unknown) => boolean

  interface NekoAssert {
    (value: unknown, message?: string): void
    ok(value: unknown, message?: string): void
    equal(actual: unknown, expected: unknown, message?: string): void
    notEqual(actual: unknown, expected: unknown, message?: string): void
    strictEqual(actual: unknown, expected: unknown, message?: string): void
    notStrictEqual(actual: unknown, expected: unknown, message?: string): void
    deepEqual(actual: unknown, expected: unknown, message?: string): void
    deepStrictEqual(actual: unknown, expected: unknown, message?: string): void
    notDeepEqual(actual: unknown, expected: unknown, message?: string): void
    notDeepStrictEqual(actual: unknown, expected: unknown, message?: string): void
    throws(fn: NekoSyncFn, expected?: NekoAssertExpected | string, message?: string): unknown
    doesNotThrow(fn: NekoSyncFn, expected?: NekoAssertExpected | string, message?: string): void
    rejects(fn: NekoMaybeAsyncFn, expected?: NekoAssertExpected | string, message?: string): Promise<unknown>
    doesNotReject(fn: NekoMaybeAsyncFn, expected?: NekoAssertExpected | string, message?: string): Promise<void>
    match(value: unknown, regexp: RegExp, message?: string): void
    doesNotMatch(value: unknown, regexp: RegExp, message?: string): void
    ifError(value: unknown): void
    fail(actual?: unknown, expected?: unknown, message?: string, operator?: string): void
    AssertionError: typeof AssertionError
    default: NekoAssert
  }

  class AssertionError extends Error {
    name: string
    code: string
    actual: unknown
    expected: unknown
    operator: string | undefined
    generatedMessage: boolean

    constructor(options: NekoAssertErrorOptions = {}) {
      const message = options.message || defaultMessage(options.actual, options.expected, options.operator)
      super(message)
      this.name = 'AssertionError'
      this.code = 'ERR_ASSERTION'
      this.actual = options.actual
      this.expected = options.expected
      this.operator = options.operator
      this.generatedMessage = !options.message
    }
  }

  function defaultMessage(actual: unknown, expected: unknown, operator: string | undefined): string {
    if (operator === 'ok') return `${inspect(actual)} == true`
    return `${inspect(actual)} ${operator || '=='} ${inspect(expected)}`
  }

  function inspect(value: unknown): string {
    try {
      if (typeof value === 'string') return `'${value}'`
      return JSON.stringify(value)
    } catch (_) {
      return String(value)
    }
  }

  function fail(actual: unknown, expected: unknown, message: string | undefined, operator: string | undefined): void {
    throw new AssertionError({ actual, expected, message, operator })
  }

  function ok(value: unknown, message?: string): void {
    if (!value) fail(value, true, message, 'ok')
  }

  function equal(actual: unknown, expected: unknown, message?: string): void {
    if (actual != expected) fail(actual, expected, message, '==')
  }

  function notEqual(actual: unknown, expected: unknown, message?: string): void {
    if (actual == expected) fail(actual, expected, message, '!=')
  }

  function strictEqual(actual: unknown, expected: unknown, message?: string): void {
    if (actual !== expected) fail(actual, expected, message, '===')
  }

  function notStrictEqual(actual: unknown, expected: unknown, message?: string): void {
    if (actual === expected) fail(actual, expected, message, '!==')
  }

  function deepStrictEqual(actual: unknown, expected: unknown, message?: string): void {
    if (!deepEqual(actual, expected)) fail(actual, expected, message, 'deepStrictEqual')
  }

  function notDeepStrictEqual(actual: unknown, expected: unknown, message?: string): void {
    if (deepEqual(actual, expected)) fail(actual, expected, message, 'notDeepStrictEqual')
  }

  function deepEqual(a: unknown, b: unknown): boolean {
    if (Object.is(a, b)) return true
    if (typeof a !== typeof b) return false
    if (!a || !b || typeof a !== 'object') return false
    if (Array.isArray(a) !== Array.isArray(b)) return false
    const aKeys = Object.keys(a)
    const bKeys = Object.keys(b)
    if (aKeys.length !== bKeys.length) return false
    for (const key of aKeys) {
      if (!Object.prototype.hasOwnProperty.call(b, key) || !deepEqual((a as Record<string, unknown>)[key], (b as Record<string, unknown>)[key])) return false
    }
    return true
  }

  function throws(fn: NekoSyncFn, expected?: NekoAssertExpected | string, message?: string): unknown {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    if (typeof fn !== 'function') fail(fn, 'function', message, 'throws')
    try {
      fn()
    } catch (error) {
      if (expected && !matchesExpected(error, expected)) {
        throw error
      }
      return error
    }
    fail(undefined, expected || 'exception', message, 'throws')
  }

  function doesNotThrow(fn: NekoSyncFn, expected?: NekoAssertExpected | string, message?: string): void {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    if (typeof fn !== 'function') fail(fn, 'function', message, 'doesNotThrow')
    try {
      fn()
    } catch (error) {
      if (!expected || matchesExpected(error, expected)) {
        const reason = (error as Error).message || error
        fail(error, undefined, message || `Got unwanted exception: ${reason}`, 'doesNotThrow')
      }
    }
  }

  function rejects(fn: NekoMaybeAsyncFn, expected?: NekoAssertExpected | string, message?: string): Promise<unknown> {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    const promise = typeof fn === 'function' ? (fn as NekoMaybeAsyncFn) : fn
    return Promise.resolve(promise).then(
      () => fail(undefined, expected || 'rejection', message, 'rejects'),
      (error: unknown) => {
        if (expected && !matchesExpected(error, expected)) throw error
        return error
      }
    )
  }

  function doesNotReject(fn: NekoMaybeAsyncFn, expected?: NekoAssertExpected | string, message?: string): Promise<void> {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    const promise = typeof fn === 'function' ? (fn as NekoMaybeAsyncFn) : fn
    return Promise.resolve(promise).catch((error: unknown) => {
      if (!expected || matchesExpected(error, expected)) {
        const reason = (error as Error).message || error
        fail(error, undefined, message || `Got unwanted rejection: ${reason}`, 'doesNotReject')
      }
    })
  }

  function matchesExpected(error: unknown, expected: NekoAssertExpected): boolean {
    if (expected instanceof RegExp) return expected.test(String((error && (error as Error).message) || error))
    if (typeof expected === 'function') return error instanceof (expected as NekoErrorCtor) || (expected as NekoErrorPredicate)(error) === true
    if (expected && typeof expected === 'object') {
      for (const key of Object.keys(expected)) {
        const expectedValue = (expected as Record<string, unknown>)[key]
        const actualValue = error && (error as Record<string, unknown>)[key]
        if (expectedValue instanceof RegExp) {
          if (!expectedValue.test(String(actualValue))) return false
        } else if (!deepEqual(actualValue, expectedValue)) {
          return false
        }
      }
      return true
    }
    return true
  }

  function match(value: unknown, regexp: RegExp, message?: string): void {
    if (!(regexp instanceof RegExp)) fail(regexp, 'RegExp', message, 'match')
    if (!regexp.test(String(value))) fail(String(value), regexp, message, 'match')
  }

  function doesNotMatch(value: unknown, regexp: RegExp, message?: string): void {
    if (!(regexp instanceof RegExp)) fail(regexp, 'RegExp', message, 'doesNotMatch')
    if (regexp.test(String(value))) fail(String(value), regexp, message, 'doesNotMatch')
  }

  function ifError(value: unknown): void {
    if (value) throw value
  }

  function assert(value: unknown, message?: string): void { ok(value, message) }
  Object.assign(assert, {
    AssertionError,
    fail,
    ok,
    equal,
    notEqual,
    strictEqual,
    notStrictEqual,
    deepEqual,
    deepStrictEqual,
    notDeepEqual: notDeepStrictEqual,
    notDeepStrictEqual,
    throws,
    doesNotThrow,
    rejects,
    doesNotReject,
    match,
    doesNotMatch,
    ifError
  })
  ;(assert as NekoAssert).default = assert

  globalThis.__nekoNodeDefine(['assert', 'node:assert', 'assert/strict', 'node:assert/strict'], assert)
})()
