;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  function wrapCpuInfo(raw) {
    if (!raw) return { model: '', speed: 0, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } }
    const times = raw.times()
    return {
      model: String(raw.model()),
      speed: Number(raw.speed()),
      times: {
        user: Number(times ? times.user() : 0),
        nice: Number(times ? times.nice() : 0),
        sys: Number(times ? times.sys() : 0),
        idle: Number(times ? times.idle() : 0),
        irq: 0
      }
    }
  }

  function wrapNetworkInterfaces(raw) {
    if (!raw) return {}
    const result = {}
    const iter = raw.entrySet().iterator()
    while (iter.hasNext()) {
      const entry = iter.next()
      const name = String(entry.getKey())
      const addrs = entry.getValue()
      result[name] = []
      for (let i = 0; i < addrs.size(); i++) {
        const addr = addrs.get(i)
        result[name].push({
          address: String(addr.address()),
          family: String(addr.family()),
          netmask: String(addr.family()) === 'IPv6' ? 'ffff:ffff:ffff:ffff::' : '255.255.255.0',
          mac: '00:00:00:00:00:00',
          internal: String(addr.address()) === '127.0.0.1' || String(addr.address()) === '::1',
          cidr: String(addr.address()).includes(':') ? String(addr.address()) + '/64' : String(addr.address()) + '/24',
          scopeid: 0
        })
      }
    }
    return result
  }

  function wrapUserInfo(raw) {
    if (!raw) return { username: 'unknown', homedir: '.', shell: 'unknown' }
    return {
      username: String(raw.username()),
      homedir: String(raw.homedir()),
      shell: String(raw.shell())
    }
  }

  const os = {
    arch: () => String(runtime.os().arch()),
    platform: () => String(runtime.os().platform()),
    cpus: () => Array.from(runtime.os().cpus(), wrapCpuInfo),
    freemem: () => Number(runtime.os().freemem()),
    totalmem: () => Number(runtime.os().totalmem()),
    homedir: () => String(runtime.os().homedir()),
    hostname: () => String(runtime.os().hostname()),
    tmpdir: () => String(runtime.os().tmpdir()),
    uptime: () => Number(runtime.os().uptime()),
    userInfo: () => wrapUserInfo(runtime.os().userInfo()),
    networkInterfaces: () => wrapNetworkInterfaces(runtime.os().networkInterfaces()),
    endianness: () => String(runtime.os().endianness()),
    loadavg: () => [runtime.os().loadavg1(), runtime.os().loadavg5(), runtime.os().loadavg15()],
    release: () => String((runtime.process && runtime.process().versions && runtime.process().versions().minecraft) || '1.0'),
    type: () => {
      const p = String(runtime.os().platform())
      return p === 'win32' ? 'Windows_NT' : p === 'darwin' ? 'Darwin' : 'Linux'
    },
    version: () => String((runtime.process && runtime.process().versions && runtime.process().versions().java) || ''),
    EOL: '\n',
    constants: {
      UV_UDP_REUSEADDR: 0, SIGTRAP: 5, SIGKILL: 9, SIGUSR1: 10, SIGUSR2: 12, SIGPIPE: 13, SIGALRM: 14, SIGTERM: 15,
      errno: {}, priority: {}, dlopen: {}, signals: {}
    },
    devNull: process.platform === 'win32' ? '\\\\.\\nul' : '/dev/null'
  }

  globalThis.__nekoNodeDefine(['os', 'node:os'], os)
})()
