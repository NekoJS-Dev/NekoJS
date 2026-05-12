;(function () {
  class AssertionError extends Error {
    constructor(options = {}) {
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

  function defaultMessage(actual, expected, operator) {
    if (operator === 'ok') return `${inspect(actual)} == true`
    return `${inspect(actual)} ${operator || '=='} ${inspect(expected)}`
  }

  function inspect(value) {
    try {
      if (typeof value === 'string') return `'${value}'`
      return JSON.stringify(value)
    } catch (_) {
      return String(value)
    }
  }

  function fail(actual, expected, message, operator) {
    throw new AssertionError({ actual, expected, message, operator })
  }

  function ok(value, message) {
    if (!value) fail(value, true, message, 'ok')
  }

  function equal(actual, expected, message) {
    if (actual != expected) fail(actual, expected, message, '==')
  }

  function notEqual(actual, expected, message) {
    if (actual == expected) fail(actual, expected, message, '!=')
  }

  function strictEqual(actual, expected, message) {
    if (actual !== expected) fail(actual, expected, message, '===')
  }

  function notStrictEqual(actual, expected, message) {
    if (actual === expected) fail(actual, expected, message, '!==')
  }

  function deepStrictEqual(actual, expected, message) {
    if (!deepEqual(actual, expected)) fail(actual, expected, message, 'deepStrictEqual')
  }

  function notDeepStrictEqual(actual, expected, message) {
    if (deepEqual(actual, expected)) fail(actual, expected, message, 'notDeepStrictEqual')
  }

  function deepEqual(a, b) {
    if (Object.is(a, b)) return true
    if (typeof a !== typeof b) return false
    if (!a || !b || typeof a !== 'object') return false
    if (Array.isArray(a) !== Array.isArray(b)) return false
    const aKeys = Object.keys(a)
    const bKeys = Object.keys(b)
    if (aKeys.length !== bKeys.length) return false
    for (const key of aKeys) {
      if (!Object.prototype.hasOwnProperty.call(b, key) || !deepEqual(a[key], b[key])) return false
    }
    return true
  }

  function throws(fn, expected, message) {
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

  function doesNotThrow(fn, expected, message) {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    if (typeof fn !== 'function') fail(fn, 'function', message, 'doesNotThrow')
    try {
      fn()
    } catch (error) {
      if (!expected || matchesExpected(error, expected)) {
        fail(error, undefined, message || `Got unwanted exception: ${error.message || error}`, 'doesNotThrow')
      }
    }
  }

  function rejects(fn, expected, message) {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    const promise = typeof fn === 'function' ? fn() : fn
    return Promise.resolve(promise).then(
      () => fail(undefined, expected || 'rejection', message, 'rejects'),
      error => {
        if (expected && !matchesExpected(error, expected)) throw error
        return error
      }
    )
  }

  function doesNotReject(fn, expected, message) {
    if (typeof expected === 'string' && message === undefined) {
      message = expected
      expected = undefined
    }
    const promise = typeof fn === 'function' ? fn() : fn
    return Promise.resolve(promise).catch(error => {
      if (!expected || matchesExpected(error, expected)) {
        fail(error, undefined, message || `Got unwanted rejection: ${error.message || error}`, 'doesNotReject')
      }
    })
  }

  function matchesExpected(error, expected) {
    if (expected instanceof RegExp) return expected.test(String(error && error.message || error))
    if (typeof expected === 'function') return error instanceof expected || expected(error) === true
    if (expected && typeof expected === 'object') {
      for (const key of Object.keys(expected)) {
        const expectedValue = expected[key]
        const actualValue = error && error[key]
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

  function ifError(value) {
    if (value) throw value
  }

  function assert(value, message) { ok(value, message) }
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
    ifError
  })
  assert.default = assert

  globalThis.__nekoNodeDefine(['assert', 'node:assert', 'assert/strict', 'node:assert/strict'], assert)
})()
