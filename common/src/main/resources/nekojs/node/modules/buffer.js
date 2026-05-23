;(function () {
  const { runtime } = globalThis.__nekoNodeInternal

  function wrapBuffer(hostBuffer) {
    if (!hostBuffer) return hostBuffer
    return new Proxy(hostBuffer, {
      get(target, prop) {
        if (prop === 'length') return target.length()
        if (prop === Symbol.toStringTag) return 'Buffer'
        if (prop === 'toJSON') return () => ({ type: 'Buffer', data: Array.from({ length: target.length() }, (_, i) => target.get(i)) })
        if (typeof prop === 'string' && /^\d+$/.test(prop)) return target.get(Number(prop))
        const value = target[prop]
        return typeof value === 'function' ? value.bind(target) : value
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
    if (Array.isArray(value)) return wrapBuffer(runtime.bufferFromString(String.fromCharCode(...value.map(v => Number(v) & 255)), 'latin1'))
    return wrapBuffer(runtime.bufferFromString(String(value ?? ''), encoding || 'utf8'))
  }
  Buffer.alloc = function (size) { return wrapBuffer(runtime.bufferAlloc(Number(size) || 0)) }
  Buffer.allocUnsafe = function (size) { return Buffer.alloc(size) }
  Buffer.isBuffer = function (value) { return !!unwrapBuffer(value) }
  Buffer.byteLength = function (value, encoding) { return runtime.bufferByteLength(String(value ?? ''), encoding || 'utf8') }
  Buffer.isEncoding = function (encoding) {
    return ['utf8', 'utf-8', 'utf16le', 'utf-16le', 'ucs2', 'ucs-2', 'ascii', 'latin1', 'binary', 'base64', 'hex'].includes(String(encoding).toLowerCase())
  }
  Buffer.concat = function (values) { return wrapBuffer(runtime.bufferConcat((values || []).map(unwrapBuffer).filter(Boolean))) }

  globalThis.__nekoNodeBuffer = { Buffer, Blob, wrapBuffer, unwrapBuffer }
  globalThis.__nekoNodeDefine(['buffer', 'node:buffer'], { Buffer, Blob })
  globalThis.Buffer = Buffer
})()
