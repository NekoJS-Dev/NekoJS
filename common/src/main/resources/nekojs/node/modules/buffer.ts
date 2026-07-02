;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  interface NekoHostBuffer {
    length(): number
    bytes(): NekoHostBytes
    get(i: number): number
    set(i: number, v: number): void
    slice(start: number, end: number): NekoHostBuffer
    fill(value: number, start: number, end: number): NekoHostBuffer
    indexOf(needle: unknown, from: number): number
    includes(needle: unknown): boolean
    copy(target: NekoHostBuffer, targetStart: number, sourceStart: number, sourceEnd: number): number
    equals(other: NekoHostBuffer): boolean
    compare(other: NekoHostBuffer): number
    toString(encoding?: string): string
    readUInt8(offset: number): number
    readInt8(offset: number): number
    readUInt16LE(offset: number): number
    readUInt16BE(offset: number): number
    readInt16LE(offset: number): number
    readInt16BE(offset: number): number
    readUInt32LE(offset: number): number
    readUInt32BE(offset: number): number
    readInt32LE(offset: number): number
    readInt32BE(offset: number): number
    readFloatLE(offset: number): number
    readFloatBE(offset: number): number
    readDoubleLE(offset: number): number
    readDoubleBE(offset: number): number
    writeUInt8(offset: number, value: number): void
    writeInt8(offset: number, value: number): void
    writeUInt16LE(offset: number, value: number): void
    writeUInt16BE(offset: number, value: number): void
    writeInt16LE(offset: number, value: number): void
    writeInt16BE(offset: number, value: number): void
    writeUInt32LE(offset: number, value: number): void
    writeUInt32BE(offset: number, value: number): void
    writeInt32LE(offset: number, value: number): void
    writeInt32BE(offset: number, value: number): void
    writeFloatLE(offset: number, value: number): void
    writeFloatBE(offset: number, value: number): void
    writeDoubleLE(offset: number, value: number): void
    writeDoubleBE(offset: number, value: number): void
  }

  interface NekoHostBytes { buffer: ArrayBuffer }
  interface NekoBufferJson { type: 'Buffer'; data: number[] }
  interface NekoBlobOptions { type?: string }
  interface NekoPartLike { toString: () => string }
  type NekoAnyFn = (...a: unknown[]) => unknown

  /** 运行时 Buffer 实例形态（Proxy 包装的宿主 buffer）。 */
  interface Buffer {
    readonly length: number
    readonly byteLength: number
    readonly buffer: ArrayBuffer
    readonly byteOffset: number
    toString(encoding?: string): string
    bytes(): NekoHostBytes
    slice(start?: number, end?: number): Buffer
    subarray(start?: number, end?: number): Buffer
    fill(value: number, start?: number, end?: number): Buffer
    indexOf(needle: unknown, fromIndex?: number): number
    includes(needle: unknown): boolean
    copy(target: unknown, targetStart?: number, sourceStart?: number, sourceEnd?: number): number
    equals(other: unknown): boolean
    compare(other: unknown): number
    toJSON(): NekoBufferJson
    [index: number]: number
  }

  /** Buffer 构造/静态调用签名（const Buffer: BufferConstructor）。 */
  interface BufferConstructor {
    new(value?: unknown, encoding?: string): Buffer
    (value?: unknown, encoding?: string): Buffer
    from(value: unknown, encoding?: string): Buffer
    alloc(size: number): Buffer
    allocUnsafe(size: number): Buffer
    allocUnsafeSlow(size: number): Buffer
    isBuffer(value: unknown): value is Buffer
    byteLength(value: unknown, encoding?: string): number
    isEncoding(encoding: string): boolean
    concat(values: Buffer[], totalLength?: number): Buffer
    compare(a: unknown, b: unknown): number
  }

  function wrapBuffer(hostBuffer: NekoHostBuffer | null | undefined): Buffer | null | undefined {
    if (!hostBuffer) return hostBuffer as Buffer | null | undefined
    return new Proxy(hostBuffer, {
      get(target, prop) {
        const t = target as NekoHostBuffer
        if (prop === 'length') return t.length()
        if (prop === 'byteLength') return t.length()
        if (prop === 'buffer') return t.bytes().buffer
        if (prop === 'byteOffset') return 0
        if (prop === Symbol.toStringTag) return 'Uint8Array'
        if (prop === Symbol.iterator) return function* () { for (let i = 0; i < t.length(); i++) yield t.get(i) }
        if (prop === 'toJSON') return () => ({ type: 'Buffer', data: Array.from({ length: t.length() }, (_, i) => t.get(i)) })
        if (typeof prop === 'string' && /^\d+$/.test(prop)) return t.get(Number(prop))
        const value = (t as unknown as Record<string | symbol, unknown>)[prop]
        if (typeof value === 'function') {
          if (prop === 'slice') return (start, end) => wrapBuffer(t.slice(Number(start) || 0, end === undefined ? t.length() : Number(end))) as Buffer
          if (prop === 'subarray') return (start, end) => wrapBuffer(t.slice(Number(start) || 0, end === undefined ? t.length() : Number(end))) as Buffer
          if (prop === 'fill') return (v, start, end) => wrapBuffer(t.fill(Number(v) || 0, Number(start) || 0, end === undefined ? t.length() : Number(end))) as Buffer
          if (prop === 'indexOf') return (needle, fromIndex) => t.indexOf(unwrapBuffer(needle), Number(fromIndex) || 0)
          if (prop === 'includes') return (needle) => t.includes(unwrapBuffer(needle))
          if (prop === 'copy') return (targetBuf, targetStart, sourceStart, sourceEnd) => t.copy(unwrapBuffer(targetBuf) as NekoHostBuffer, Number(targetStart) || 0, Number(sourceStart) || 0, sourceEnd === undefined ? t.length() : Number(sourceEnd))
          if (prop === 'equals') return (other) => t.equals(unwrapBuffer(other) as NekoHostBuffer)
          if (prop === 'compare') return (other) => t.compare(unwrapBuffer(other) as NekoHostBuffer)
          if (prop === 'toString') return (encoding) => t.toString(encoding || 'utf8')
          // Multi-byte read methods
          if (prop === 'readUInt8') return (offset) => t.readUInt8(Number(offset))
          if (prop === 'readInt8') return (offset) => t.readInt8(Number(offset))
          if (prop === 'readUInt16LE') return (offset) => t.readUInt16LE(Number(offset))
          if (prop === 'readUInt16BE') return (offset) => t.readUInt16BE(Number(offset))
          if (prop === 'readInt16LE') return (offset) => t.readInt16LE(Number(offset))
          if (prop === 'readInt16BE') return (offset) => t.readInt16BE(Number(offset))
          if (prop === 'readUInt32LE') return (offset) => t.readUInt32LE(Number(offset))
          if (prop === 'readUInt32BE') return (offset) => t.readUInt32BE(Number(offset))
          if (prop === 'readInt32LE') return (offset) => t.readInt32LE(Number(offset))
          if (prop === 'readInt32BE') return (offset) => t.readInt32BE(Number(offset))
          if (prop === 'readFloatLE') return (offset) => t.readFloatLE(Number(offset))
          if (prop === 'readFloatBE') return (offset) => t.readFloatBE(Number(offset))
          if (prop === 'readDoubleLE') return (offset) => t.readDoubleLE(Number(offset))
          if (prop === 'readDoubleBE') return (offset) => t.readDoubleBE(Number(offset))
          if (prop === 'writeUInt8') return (v, offset) => { t.writeUInt8(Number(offset), Number(v)); return offset + 1 }
          if (prop === 'writeInt8') return (v, offset) => { t.writeInt8(Number(offset), Number(v)); return offset + 1 }
          if (prop === 'writeUInt16LE') return (v, offset) => { t.writeUInt16LE(Number(offset), Number(v)); return offset + 2 }
          if (prop === 'writeUInt16BE') return (v, offset) => { t.writeUInt16BE(Number(offset), Number(v)); return offset + 2 }
          if (prop === 'writeInt16LE') return (v, offset) => { t.writeInt16LE(Number(offset), Number(v)); return offset + 2 }
          if (prop === 'writeInt16BE') return (v, offset) => { t.writeInt16BE(Number(offset), Number(v)); return offset + 2 }
          if (prop === 'writeUInt32LE') return (v, offset) => { t.writeUInt32LE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeUInt32BE') return (v, offset) => { t.writeUInt32BE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeInt32LE') return (v, offset) => { t.writeInt32LE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeInt32BE') return (v, offset) => { t.writeInt32BE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeFloatLE') return (v, offset) => { t.writeFloatLE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeFloatBE') return (v, offset) => { t.writeFloatBE(Number(offset), Number(v)); return offset + 4 }
          if (prop === 'writeDoubleLE') return (v, offset) => { t.writeDoubleLE(Number(offset), Number(v)); return offset + 8 }
          if (prop === 'writeDoubleBE') return (v, offset) => { t.writeDoubleBE(Number(offset), Number(v)); return offset + 8 }
          return (value as NekoAnyFn).bind(t)
        }
        return value
      },
      set(target, prop, value) {
        const t = target as NekoHostBuffer
        if (typeof prop === 'string' && /^\d+$/.test(prop)) {
          t.set(Number(prop), Number(value))
          return true
        }
        ;(t as unknown as Record<string | symbol, unknown>)[prop] = value
        return true
      }
    }) as Buffer
  }

  // 保留原运行时语义：unwrapBuffer 返回 Proxy 包装的 buffer 本身（宿主方法的 .bytes 句柄）。
  // 宿主侧 compare/copy/indexOf/includes 经 Proxy.get 委托到 target，故此处只判定形态、原样回传。
  function unwrapBuffer(value: unknown): NekoHostBuffer | undefined {
    if (value && typeof (value as Record<string, unknown>).bytes === 'function') return value as unknown as NekoHostBuffer
    return undefined
  }

  function partToString(part: unknown): string {
    if (part && typeof (part as Record<string, unknown>).toString === 'function') return (part as unknown as NekoPartLike).toString()
    return String(part ?? '')
  }

  class Blob {
    _parts: unknown[]
    type: string
    size: number

    constructor(parts?: unknown[], options?: NekoBlobOptions) {
      this._parts = Array.from(parts || [])
      let typeStr = ''
      if (options && options.type) typeStr = String(options.type)
      this.type = typeStr
      this.size = this._parts.reduce((size, part) => size + partToString(part).length, 0)
    }

    text(): Promise<string> {
      return Promise.resolve(this._parts.map(partToString).join(''))
    }

    arrayBuffer(): Promise<ArrayBuffer> {
      return Promise.resolve(Buffer.from(this._parts.map(partToString).join('')).buffer)
    }

    slice(start?: number, end?: number, type?: string): Blob {
      const text = this._parts.map(partToString).join('').slice(start || 0, end)
      return new Blob([text], { type: type || this.type })
    }
  }

  const Buffer: BufferConstructor = function Buffer(value?: unknown, encoding?: string): Buffer {
    return Buffer.from(value, encoding)
  } as BufferConstructor

  Buffer.from = function (value: unknown, encoding?: string): Buffer {
    if (value && typeof (value as Record<string, unknown>).bytes === 'function') return wrapBuffer(value as unknown as NekoHostBuffer) as Buffer
    if (value instanceof ArrayBuffer || (typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer)) {
      return wrapBuffer(runtime.bufferFromString(String.fromCharCode(...new Uint8Array(value)), 'latin1')) as Buffer
    }
    if (Array.isArray(value)) return wrapBuffer(runtime.bufferFromString(String.fromCharCode(...value.map((v: unknown) => Number(v) & 255)), 'latin1')) as Buffer
    return wrapBuffer(runtime.bufferFromString(String(value ?? ''), encoding || 'utf8')) as Buffer
  }
  Buffer.alloc = function (size: number): Buffer { return wrapBuffer(runtime.bufferAlloc(Number(size) || 0)) as Buffer }
  Buffer.allocUnsafe = function (size: number): Buffer { return Buffer.alloc(size) }
  Buffer.allocUnsafeSlow = function (size: number): Buffer { return Buffer.alloc(size) }
  Buffer.isBuffer = function (value: unknown): value is Buffer { return !!unwrapBuffer(value) }
  Buffer.byteLength = function (value: unknown, encoding?: string): number { return runtime.bufferByteLength(String(value ?? ''), encoding || 'utf8') }
  Buffer.isEncoding = function (encoding: string): boolean {
    return ['utf8', 'utf-8', 'utf16le', 'utf-16le', 'ucs2', 'ucs-2', 'ascii', 'latin1', 'binary', 'base64', 'hex'].includes(String(encoding).toLowerCase())
  }
  Buffer.concat = function (values: Buffer[], totalLength?: number): Buffer { return wrapBuffer(runtime.bufferConcat((values || []).map((v: Buffer) => unwrapBuffer(v)).filter(Boolean) as NekoHostBuffer[])) as Buffer }
  Buffer.compare = function (a: unknown, b: unknown): number {
    const ua = unwrapBuffer(a), ub = unwrapBuffer(b)
    if (!ua && !ub) return 0
    if (!ua) return -1
    if (!ub) return 1
    return ua.compare(ub)
  }

  globalThis.__nekoNodeBuffer = { Buffer, Blob, wrapBuffer, unwrapBuffer }
  globalThis.__nekoNodeDefine(['buffer', 'node:buffer'], { Buffer, Blob, constants: { MAX_LENGTH: 0x7FFFFFFF, MAX_STRING_LENGTH: 0x1FFFFFFFE } })
  globalThis.Buffer = Buffer
})()
