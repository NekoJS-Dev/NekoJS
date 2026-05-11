;(function () {
  const util = {
    format(format, ...args) {
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
    },
    inspect(value) {
      try { return JSON.stringify(value, null, 2) } catch { return String(value) }
    },
    promisify(fn) {
      return (...args) => new Promise((resolve, reject) => fn(...args, (err, value) => err ? reject(err) : resolve(value)))
    },
    callbackify(fn) {
      return (...args) => {
        const cb = args.pop()
        Promise.resolve(fn(...args)).then(value => cb(null, value), err => cb(err))
      }
    },
    types: {
      isPromise: value => !!(value && typeof value.then === 'function'),
      isProxy: () => false,
      isNativeError: value => value instanceof Error,
      isDate: value => value instanceof Date,
      isRegExp: value => value instanceof RegExp
    }
  }

  globalThis.__nekoNodeDefine(['util', 'node:util'], util)
})()
