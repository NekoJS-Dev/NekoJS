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
    setInterval: async function * (delay, value) {
      let queue = []
      let resume
      const id = timers.setInterval(() => {
        if (resume) {
          const next = resume
          resume = undefined
          next(value)
        } else {
          queue.push(value)
        }
      }, delay)
      try {
        while (true) {
          if (queue.length > 0) {
            yield queue.shift()
          } else {
            yield await new Promise(resolve => { resume = resolve })
          }
        }
      } finally {
        timers.clearInterval(id)
      }
    }
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
