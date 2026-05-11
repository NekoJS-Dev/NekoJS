;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const timers = globalThis.__nekoNodeTimers

  const process = {
    cwd: () => String(runtime.process().cwd()),
    chdir: value => runtime.process().chdir(String(value)),
    get platform() { return String(runtime.process().platform()) },
    get versions() { return runtime.process().versions() },
    get env() { return runtime.process().env() },
    nextTick: (callback, ...args) => timers.setImmediate(callback, ...args)
  }

  globalThis.__nekoNodeDefine(['process', 'node:process'], process)
  globalThis.process = process
})()
