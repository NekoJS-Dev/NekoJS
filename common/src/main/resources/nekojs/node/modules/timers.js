;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  const timers = {
    setTimeout: (callback, delay, ...args) => runtime.timers().setTimeout(callback, Number(delay) || 0, ...args),
    clearTimeout: id => runtime.timers().clearTimeout(Number(id)),
    setInterval: (callback, delay, ...args) => runtime.timers().setInterval(callback, Number(delay) || 0, ...args),
    clearInterval: id => runtime.timers().clearInterval(Number(id)),
    setImmediate: (callback, ...args) => runtime.timers().setImmediate(callback, ...args),
    clearImmediate: id => runtime.timers().clearImmediate(Number(id))
  }

  const timerPromises = {
    setTimeout: (delay, value) => new Promise(resolve => timers.setTimeout(() => resolve(value), delay)),
    setImmediate: value => new Promise(resolve => timers.setImmediate(() => resolve(value))),
    setInterval: async function * () { throw new Error('timers/promises.setInterval is not implemented yet') }
  }

  globalThis.__nekoNodeTimers = timers
  globalThis.__nekoNodeDefine(['timers', 'node:timers'], timers)
  globalThis.__nekoNodeDefine(['timers/promises', 'node:timers/promises'], timerPromises)

  globalThis.setTimeout = timers.setTimeout
  globalThis.clearTimeout = timers.clearTimeout
  globalThis.setInterval = timers.setInterval
  globalThis.clearInterval = timers.clearInterval
  globalThis.setImmediate = timers.setImmediate
  globalThis.clearImmediate = timers.clearImmediate
})()
