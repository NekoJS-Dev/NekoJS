;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  interface NekoTimerHandle { __nekoTimerHandle: true }
  type TimerCallback = (...args: unknown[]) => void

  const timers = {
    setTimeout: (callback: TimerCallback, delay?: number, ...args: unknown[]): NekoTimerHandle => runtime.timers().setTimeout(callback, Number(delay) || 0, ...args),
    clearTimeout: (id: unknown): void => { runtime.timers().clearTimeout(Number(id)) },
    setInterval: (callback: TimerCallback, delay?: number, ...args: unknown[]): NekoTimerHandle => runtime.timers().setInterval(callback, Number(delay) || 0, ...args),
    clearInterval: (id: unknown): void => { runtime.timers().clearInterval(Number(id)) },
    setImmediate: (callback: TimerCallback, ...args: unknown[]): NekoTimerHandle => runtime.timers().setImmediate(callback, ...args),
    clearImmediate: (id: unknown): void => { runtime.timers().clearImmediate(Number(id)) }
  }

  const timerPromises = {
    setTimeout: (delay?: number, value?: unknown): Promise<unknown> => new Promise(resolve => timers.setTimeout(() => resolve(value), delay)),
    setImmediate: (value?: unknown): Promise<unknown> => new Promise(resolve => timers.setImmediate(() => resolve(value))),
    setInterval: async function * (delay: number, value?: unknown): AsyncIterable<unknown> {
      let queue: unknown[] = []
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
