;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  function wrapBuffer(hostBuffer) {
    if (!hostBuffer) return hostBuffer
    return new Proxy(hostBuffer, {
      get(target, prop) {
        if (prop === 'length') return target.length()
        if (prop === 'byteLength') return target.length()
        if (prop === 'buffer') return target.bytes().buffer
        if (prop === 'byteOffset') return 0
        if (prop === Symbol.toStringTag) return 'Uint8Array'
        if (prop === Symbol.iterator) return function* () { for (let i = 0; i < target.length(); i++) yield target.get(i) }
        if (prop === 'toJSON') return () => ({ type: 'Buffer', data: Array.from({ length: target.length() }, (_, i) => target.get(i)) })
        if (typeof prop === 'string' && /^\d+$/.test(prop)) return target.get(Number(prop))
        const value = target[prop]
        if (typeof value === 'function') {
          if (prop === 'slice') return (start, end) => wrapBuffer(target.slice(Number(start) || 0, end === undefined ? target.length() : Number(end)))
          if (prop === 'subarray') return (start, end) => wrapBuffer(target.slice(Number(start) || 0, end === undefined ? target.length() : Number(end)))
          if (prop === 'fill') return (value, start, end) => wrapBuffer(target.fill(Number(value) || 0, Number(start) || 0, end === undefined ? target.length() : Number(end)))
          if (prop === 'indexOf') return (needle, fromIndex) => target.indexOf(unwrapBuffer(needle), Number(fromIndex) || 0)
          if (prop === 'includes') return needle => target.includes(unwrapBuffer(needle))
          if (prop === 'copy') return (targetBuf, targetStart, sourceStart, sourceEnd) => target.copy(unwrapBuffer(targetBuf), Number(targetStart) || 0, Number(sourceStart) || 0, sourceEnd === undefined ? target.length() : Number(sourceEnd))
          if (prop === 'equals') return other => target.equals(unwrapBuffer(other))
          if (prop === 'compare') return other => target.compare(unwrapBuffer(other))
          if (prop === 'toString') return (encoding) => target.toString(encoding || 'utf8')
          // Multi-byte read methods
          if (prop === 'readUInt8') return (offset) => target.readUInt8(Number(offset))
          if (prop === 'readInt8') return (offset) => target.readInt8(Number(offset))
          if (prop === 'readUInt16LE') return (offset) => target.readUInt16LE(Number(offset))
          if (prop === 'readUInt16BE') return (offset) => target.readUInt16BE(Number(offset))
          if (prop === 'readInt16LE') return (offset) => target.readInt16LE(Number(offset))
          if (prop === 'readInt16BE') return (offset) => target.readInt16BE(Number(offset))
          if (prop === 'readUInt32LE') return (offset) => target.readUInt32LE(Number(offset))
          if (prop === 'readUInt32BE') return (offset) => target.readUInt32BE(Number(offset))
          if (prop === 'readInt32LE') return (offset) => target.readInt32LE(Number(offset))
          if (prop === 'readInt32BE') return (offset) => target.readInt32BE(Number(offset))
          if (prop === 'readFloatLE') return (offset) => target.readFloatLE(Number(offset))
          if (prop === 'readFloatBE') return (offset) => target.readFloatBE(Number(offset))
          if (prop === 'readDoubleLE') return (offset) => target.readDoubleLE(Number(offset))
          if (prop === 'readDoubleBE') return (offset) => target.readDoubleBE(Number(offset))
          if (prop === 'writeUInt8') return (value, offset) => { target.writeUInt8(Number(offset), Number(value)); return offset + 1 }
          if (prop === 'writeInt8') return (value, offset) => { target.writeInt8(Number(offset), Number(value)); return offset + 1 }
          if (prop === 'writeUInt16LE') return (value, offset) => { target.writeUInt16LE(Number(offset), Number(value)); return offset + 2 }
          if (prop === 'writeUInt16BE') return (value, offset) => { target.writeUInt16BE(Number(offset), Number(value)); return offset + 2 }
          if (prop === 'writeInt16LE') return (value, offset) => { target.writeInt16LE(Number(offset), Number(value)); return offset + 2 }
          if (prop === 'writeInt16BE') return (value, offset) => { target.writeInt16BE(Number(offset), Number(value)); return offset + 2 }
          if (prop === 'writeUInt32LE') return (value, offset) => { target.writeUInt32LE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeUInt32BE') return (value, offset) => { target.writeUInt32BE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeInt32LE') return (value, offset) => { target.writeInt32LE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeInt32BE') return (value, offset) => { target.writeInt32BE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeFloatLE') return (value, offset) => { target.writeFloatLE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeFloatBE') return (value, offset) => { target.writeFloatBE(Number(offset), Number(value)); return offset + 4 }
          if (prop === 'writeDoubleLE') return (value, offset) => { target.writeDoubleLE(Number(offset), Number(value)); return offset + 8 }
          if (prop === 'writeDoubleBE') return (value, offset) => { target.writeDoubleBE(Number(offset), Number(value)); return offset + 8 }
          return value.bind(target)
        }
        return value
      },
      set(target, prop, value) {
        if (typeof prop === 'string' && /^\d+$/.test(prop)) {
          target.set(Number(prop), Number(value))
          return true
        }
        target[prop] = value
        return true
      }
    })
  }

  function unwrapBuffer(value) {
    return value && typeof value.bytes === 'function' ? value : undefined
  }

  class Blob {
    constructor(parts, options) {
      this._parts = Array.from(parts || [])
      this.type = options && options.type ? String(options.type) : ''
      this.size = this._parts.reduce((size, part) => size + String(part && typeof part.toString === 'function' ? part.toString() : part ?? '').length, 0)
    }

    text() {
      return Promise.resolve(this._parts.map(part => part && typeof part.toString === 'function' ? part.toString() : String(part ?? '')).join(''))
    }

    arrayBuffer() {
      return Promise.resolve(Buffer.from(this._parts.map(part => part && typeof part.toString === 'function' ? part.toString() : String(part ?? '')).join('')).bytes())
    }

    slice(start, end, type) {
      const text = this._parts.map(part => part && typeof part.toString === 'function' ? part.toString() : String(part ?? '')).join('').slice(start || 0, end)
      return new Blob([text], { type: type || this.type })
    }
  }

  function Buffer(value, encoding) {
    return Buffer.from(value, encoding)
  }

  Buffer.from = function (value, encoding) {
    if (value && typeof value.bytes === 'function') return wrapBuffer(value)
    if (value instanceof ArrayBuffer || (typeof SharedArrayBuffer !== 'undefined' && value instanceof SharedArrayBuffer)) {
      return wrapBuffer(runtime.bufferFromString(String.fromCharCode(...new Uint8Array(value)), 'latin1'))
    }
    if (Array.isArray(value)) return wrapBuffer(runtime.bufferFromString(String.fromCharCode(...value.map(v => Number(v) & 255)), 'latin1'))
    return wrapBuffer(runtime.bufferFromString(String(value ?? ''), encoding || 'utf8'))
  }
  Buffer.alloc = function (size) { return wrapBuffer(runtime.bufferAlloc(Number(size) || 0)) }
  Buffer.allocUnsafe = function (size) { return Buffer.alloc(size) }
  Buffer.allocUnsafeSlow = function (size) { return Buffer.alloc(size) }
  Buffer.isBuffer = function (value) { return !!unwrapBuffer(value) }
  Buffer.byteLength = function (value, encoding) { return runtime.bufferByteLength(String(value ?? ''), encoding || 'utf8') }
  Buffer.isEncoding = function (encoding) {
    return ['utf8', 'utf-8', 'utf16le', 'utf-16le', 'ucs2', 'ucs-2', 'ascii', 'latin1', 'binary', 'base64', 'hex'].includes(String(encoding).toLowerCase())
  }
  Buffer.concat = function (values, totalLength) { return wrapBuffer(runtime.bufferConcat((values || []).map(unwrapBuffer).filter(Boolean))) }
  Buffer.compare = function (a, b) {
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
