;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const timers = globalThis.__nekoNodeTimers
  const startedAtMillis: number = Date.now()

  let exitCode: number = 0

  interface NekoMemoryUsage { rss: number; heapTotal: number; heapUsed: number; external: number; arrayBuffers: number }
  interface NekoCpuUsage { user: number; system: number }
  interface NekoHrtime { (previous?: [number, number]): [number, number]; bigint(previous?: [number, number]): bigint }

  function hrtime(previous?: [number, number]): [number, number] {
    const seconds: number = Math.floor((Date.now() - startedAtMillis) / 1000)
    const nanos: number = Math.floor((Date.now() - startedAtMillis) * 1e6 - seconds * 1e9)
    const result: [number, number] = [seconds, nanos]
    if (previous) {
      result[0] -= Number(previous[0]) || 0
      const deltaNanos: number = result[1] - (Number(previous[1]) || 0)
      if (deltaNanos < 0) { result[0]--; result[1] = 1e9 + deltaNanos }
      else result[1] = deltaNanos
    }
    return result
  }

  hrtime.bigint = function (previous?: [number, number]): bigint {
    const [seconds, nanos] = hrtime(previous)
    return BigInt(seconds) * 1000000000n + BigInt(nanos)
  }

  function wrapMemoryUsage(raw: unknown): NekoMemoryUsage {
    if (!raw) return { rss: 0, heapTotal: 0, heapUsed: 0, external: 0, arrayBuffers: 0 }
    return {
      rss: Number(raw.rss()), heapTotal: Number(raw.heapTotal()), heapUsed: Number(raw.heapUsed()),
      external: Number(raw.external()), arrayBuffers: Number(raw.arrayBuffers())
    }
  }

  function wrapCpuUsage(raw: unknown): NekoCpuUsage {
    if (!raw) return { user: 0, system: 0 }
    return { user: Number(raw.user()), system: Number(raw.system()) }
  }

  interface NekoProcessModule {
    argv: string[]
    exitCode: number | string
    readonly pid: number
    readonly platform: string
    readonly versions: Record<string, string>
    readonly env: Record<string, string>
    cwd(): string
    chdir(path: string): void
    uptime(): number
    hrtime: NekoHrtime
    memoryUsage(): NekoMemoryUsage
    cpuUsage(): NekoCpuUsage
    nextTick(callback: (...args: unknown[]) => void, ...args: unknown[]): unknown
  }

  const process: NekoProcessModule = {
    argv: ['nekojs'],
    cwd(): string { return String(runtime.process().cwd()) },
    chdir(value): void { runtime.process().chdir(String(value)) },
    get platform(): string { return String(runtime.process().platform()) },
    get versions(): Record<string, string> { return runtime.process().versions() },
    get env(): Record<string, string> {
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
    get exitCode(): number | string { return exitCode },
    set exitCode(value) { exitCode = value },
    get pid(): number { try { return Number(runtime.process().pid()) } catch (_) { return 0 } },
    uptime(): number { return Math.max(0, (Date.now() - startedAtMillis) / 1000) },
    hrtime,
    memoryUsage(): NekoMemoryUsage { return wrapMemoryUsage(runtime.process().memoryUsage()) },
    cpuUsage(): NekoCpuUsage { return wrapCpuUsage(runtime.process().cpuUsage()) },
    nextTick(callback, ...args) {
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
