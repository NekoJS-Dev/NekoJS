;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const timers = globalThis.__nekoNodeTimers
  const startedAtMillis: number = Date.now()
  /** 单调时钟基线（宿主 System.nanoTime）：hrtime/performance 用它，Date.now 非单调且只有毫秒。 */
  const startedAtNanos: number = Number(runtime.nanoTime())

  let exitCode: number = 0

  interface NekoMemoryUsage { rss: number; heapTotal: number; heapUsed: number; external: number; arrayBuffers: number }
  interface NekoCpuUsage { user: number; system: number }
  interface NekoHrtime { (previous?: [number, number]): [number, number]; bigint(previous?: [number, number]): bigint }

  function hrtime(previous?: [number, number]): [number, number] {
    const deltaNanos = Number(runtime.nanoTime()) - startedAtNanos
    const seconds = Math.floor(deltaNanos / 1e9)
    const result: [number, number] = [seconds, deltaNanos - seconds * 1e9]
    if (previous) {
      result[0] -= Number(previous[0]) || 0
      const deltaNanosDiff = result[1] - (Number(previous[1]) || 0)
      if (deltaNanosDiff < 0) { result[0]--; result[1] = 1e9 + deltaNanosDiff }
      else result[1] = deltaNanosDiff
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

  function reportError(origin: string, error: unknown): void {
    if (typeof console !== 'undefined' && typeof console.error === 'function') {
      console.error('Uncaught exception in ' + origin + ':', error)
    }
  }

  interface NekoVoidFn { (): void }

  /** GraalJS 无 queueMicrotask：nextTick 退化为 setImmediate 会晚一个游戏 tick。这里用 Promise
   * 微任务自建队列——同步栈结束后的第一个微任务就排空，顺序保证远好于等 tick（仍非 Node 的
   * 「先于一切微任务」，引擎层面无法完全复刻）。 */
  const tickQueue: NekoVoidFn[] = []
  let tickDrainScheduled = false

  function drainTicks(): void {
    tickDrainScheduled = false
    const queue = tickQueue.splice(0)
    for (const run of queue) run()
  }

  function scheduleTickDrain(): void {
    if (tickDrainScheduled) return
    tickDrainScheduled = true
    Promise.resolve().then(drainTicks)
  }

  /** stdout/stderr 最小实现：write 不追加换行（与 Node 语义一致），转发到宿主日志。 */
  function createStdStream(useStderr: boolean): NekoStdStream {
    return {
      isTTY: false,
      write(chunk: unknown): boolean {
        const text = String(chunk ?? '')
        const body = text.endsWith('\n') ? text.slice(0, -1) : text
        if (useStderr) console.error(body)
        else console.log(body)
        return true
      }
    }
  }

  interface NekoStdStream {
    isTTY: boolean
    write(chunk: unknown): boolean
  }

  interface NekoProcessModule {
    argv: string[]
    exitCode: number | string
    readonly pid: number
    readonly platform: string
    readonly versions: Record<string, string>
    readonly env: Record<string, string>
    readonly stdout: NekoStdStream
    readonly stderr: NekoStdStream
    cwd(): string
    chdir(path: string): void
    exit(code?: number | string): void
    uptime(): number
    hrtime: NekoHrtime
    memoryUsage(): NekoMemoryUsage
    cpuUsage(previousValue?: NekoCpuUsage): NekoCpuUsage
    nextTick(callback: (...args: unknown[]) => void, ...args: unknown[]): unknown
  }

  const process: NekoProcessModule = {
    argv: ['nekojs'],
    cwd(): string { return String(runtime.process().cwd()) },
    chdir(value): void { runtime.process().chdir(String(value)) },
    get platform(): string { return String(runtime.process().platform()) },
    get versions(): Record<string, string> { return runtime.process().versions() },
    get stdout(): NekoStdStream { return stdoutStream },
    get stderr(): NekoStdStream { return stderrStream },
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
    cpuUsage(previousValue): NekoCpuUsage {
      const current = wrapCpuUsage(runtime.process().cpuUsage())
      if (previousValue && typeof (previousValue as NekoCpuUsage).user === 'number') {
        return {
          user: current.user - (previousValue as NekoCpuUsage).user,
          system: current.system - ((previousValue as NekoCpuUsage).system || 0)
        }
      }
      return current
    },
    exit(code?: number | string): void {
      if (typeof code === 'number' || typeof code === 'string') exitCode = code
      if (typeof console !== 'undefined' && typeof console.warn === 'function') {
        console.warn('process.exit() is a no-op in the NekoJS sandbox; script execution continues.')
      }
    },
    nextTick(callback, ...args) {
      tickQueue.push(() => {
        try {
          callback(...args)
        } catch (error) {
          reportError('process.nextTick', error)
        }
      })
      scheduleTickDrain()
    }
  }

  const stdoutStream = createStdStream(false)
  const stderrStream = createStdStream(true)

  globalThis.__nekoNodeDefine(['process', 'node:process'], process)
  globalThis.process = process

  globalThis.queueMicrotask = function queueMicrotask(callback: NekoVoidFn): void {
    Promise.resolve().then(() => {
      try {
        callback()
      } catch (error) {
        reportError('queueMicrotask', error)
      }
    })
  }

  globalThis.performance = {
    now(): number { return (Number(runtime.nanoTime()) - startedAtNanos) / 1e6 }
  }
})()
