;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const timers = globalThis.__nekoNodeTimers
  const startedAtMillis = Date.now()

  let exitCode = 0

  function hrtime(previous) {
    const seconds = Math.floor((Date.now() - startedAtMillis) / 1000)
    const nanos = Math.floor((Date.now() - startedAtMillis) * 1e6 - seconds * 1e9)
    const result = [seconds, nanos]
    if (previous) {
      result[0] -= Number(previous[0]) || 0
      const deltaNanos = result[1] - (Number(previous[1]) || 0)
      if (deltaNanos < 0) { result[0]--; result[1] = 1e9 + deltaNanos }
      else result[1] = deltaNanos
    }
    return result
  }

  hrtime.bigint = function (previous) {
    const [seconds, nanos] = hrtime(previous)
    return BigInt(seconds) * 1000000000n + BigInt(nanos)
  }

  function wrapMemoryUsage(raw) {
    if (!raw) return { rss: 0, heapTotal: 0, heapUsed: 0, external: 0, arrayBuffers: 0 }
    return {
      rss: Number(raw.rss()), heapTotal: Number(raw.heapTotal()), heapUsed: Number(raw.heapUsed()),
      external: Number(raw.external()), arrayBuffers: Number(raw.arrayBuffers())
    }
  }

  function wrapCpuUsage(raw) {
    if (!raw) return { user: 0, system: 0 }
    return { user: Number(raw.user()), system: Number(raw.system()) }
  }

  const process = {
    argv: ['nekojs'],
    cwd: () => String(runtime.process().cwd()),
    chdir: value => runtime.process().chdir(String(value)),
    get platform() { return String(runtime.process().platform()) },
    get versions() { return runtime.process().versions() },
    get env() {
      const cache = process._envCache
      if (cache) return cache
      try {
        const raw = runtime.process().env()
        const result = Object.create(null)
        const iter = raw.entrySet().iterator()
        while (iter.hasNext()) {
          const entry = iter.next()
          result[String(entry.getKey())] = String(entry.getValue())
        }
        return process._envCache = result
      } catch (_) {
        return process._envCache = Object.create(null)
      }
    },
    get exitCode() { return exitCode },
    set exitCode(value) { exitCode = value },
    get pid() { try { return Number(runtime.process().pid()) } catch (_) { return 0 } },
    uptime: () => Math.max(0, (Date.now() - startedAtMillis) / 1000),
    hrtime,
    memoryUsage: () => wrapMemoryUsage(runtime.process().memoryUsage()),
    cpuUsage: () => wrapCpuUsage(runtime.process().cpuUsage()),
    nextTick: (callback, ...args) => {
      if (typeof queueMicrotask === 'function') {
        queueMicrotask(() => { try { callback(...args) } catch (_) {} })
      } else {
        timers.setImmediate(callback, ...args)
      }
    }
  }

  globalThis.__nekoNodeDefine(['process', 'node:process'], process)
  globalThis.process = process
})()
