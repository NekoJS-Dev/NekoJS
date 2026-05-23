;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const timers = globalThis.__nekoNodeTimers
  const startedAtMillis = Date.now()
  const startedAtNanos = nowNanos()

  let exitCode = 0

  function nowNanos() {
    return Math.floor(Date.now() * 1e6)
  }

  function hrtime(previous) {
    let nanos = nowNanos() - startedAtNanos
    if (previous) {
      nanos -= (Number(previous[0]) || 0) * 1e9 + (Number(previous[1]) || 0)
    }
    const seconds = Math.floor(nanos / 1e9)
    return [seconds, Math.floor(nanos - seconds * 1e9)]
  }

  const process = {
    argv: ['nekojs'],
    cwd: () => String(runtime.process().cwd()),
    chdir: value => runtime.process().chdir(String(value)),
    get platform() { return String(runtime.process().platform()) },
    get versions() { return runtime.process().versions() },
    get env() { return runtime.process().env() },
    get exitCode() { return exitCode },
    set exitCode(value) { exitCode = value },
    get pid() {
      try {
        return Number(runtime.process().pid())
      } catch (_) {
        return 0
      }
    },
    uptime: () => Math.max(0, (Date.now() - startedAtMillis) / 1000),
    hrtime,
    nextTick: (callback, ...args) => timers.setImmediate(callback, ...args)
  }

  globalThis.__nekoNodeDefine(['process', 'node:process'], process)
  globalThis.process = process
})()
