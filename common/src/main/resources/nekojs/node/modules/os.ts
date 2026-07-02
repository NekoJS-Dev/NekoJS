;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  interface NekoCpuInfo { model: string; speed: number; times: { user: number; nice: number; sys: number; idle: number; irq: number } }
  interface NekoUserInfo { username: string; homedir: string; shell: string }
  interface NekoNetworkInterfaceInfo { address: string; family: string; netmask: string; mac: string; internal: boolean; cidr: string; scopeid: number }

  function wrapCpuInfo(raw: unknown): NekoCpuInfo {
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

  function wrapNetworkInterfaces(raw: unknown): Record<string, NekoNetworkInterfaceInfo[]> {
    if (!raw) return {}
    const result: Record<string, NekoNetworkInterfaceInfo[]> = {}
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

  function wrapUserInfo(raw: unknown): NekoUserInfo {
    if (!raw) return { username: 'unknown', homedir: '.', shell: 'unknown' }
    return {
      username: String(raw.username()),
      homedir: String(raw.homedir()),
      shell: String(raw.shell())
    }
  }

  const os = {
    arch: (): string => String(runtime.os().arch()),
    platform: (): string => String(runtime.os().platform()),
    cpus: (): NekoCpuInfo[] => Array.from(runtime.os().cpus(), wrapCpuInfo),
    freemem: (): number => Number(runtime.os().freemem()),
    totalmem: (): number => Number(runtime.os().totalmem()),
    homedir: (): string => String(runtime.os().homedir()),
    hostname: (): string => String(runtime.os().hostname()),
    tmpdir: (): string => String(runtime.os().tmpdir()),
    uptime: (): number => Number(runtime.os().uptime()),
    userInfo: (): NekoUserInfo => wrapUserInfo(runtime.os().userInfo()),
    networkInterfaces: (): Record<string, NekoNetworkInterfaceInfo[]> => wrapNetworkInterfaces(runtime.os().networkInterfaces()),
    endianness: (): string => String(runtime.os().endianness()),
    loadavg: (): [number, number, number] => [runtime.os().loadavg1(), runtime.os().loadavg5(), runtime.os().loadavg15()],
    release: (): string => String((runtime.process && runtime.process().versions && runtime.process().versions().minecraft) || '1.0'),
    type: (): string => {
      const p = String(runtime.os().platform())
      return p === 'win32' ? 'Windows_NT' : p === 'darwin' ? 'Darwin' : 'Linux'
    },
    version: (): string => String((runtime.process && runtime.process().versions && runtime.process().versions().java) || ''),
    EOL: '\n',
    constants: {
      UV_UDP_REUSEADDR: 0, SIGTRAP: 5, SIGKILL: 9, SIGUSR1: 10, SIGUSR2: 12, SIGPIPE: 13, SIGALRM: 14, SIGTERM: 15,
      errno: {}, priority: {}, dlopen: {}, signals: {}
    },
    devNull: process.platform === 'win32' ? '\\\\.\\nul' : '/dev/null'
  }

  globalThis.__nekoNodeDefine(['os', 'node:os'], os)
})()
