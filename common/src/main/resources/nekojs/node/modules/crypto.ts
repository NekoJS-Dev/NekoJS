;(function () {
  const { runtime } = globalThis.__nekoNodeInternal
  const { wrapBuffer, unwrapBuffer } = globalThis.__nekoNodeBuffer

  /** 宿主 hash/hmac 对象形态（NekoNodeCrypto.NekoNodeHash / NekoNodeHmac）。 */
  interface NekoHostHash {
    updateBuffer(data: unknown): NekoHostHash
    updateString(data: string, encoding: string): NekoHostHash
    digestBuffer(): unknown
    digestString(encoding: string): string
  }

  interface NekoDigest { update(data: unknown, encoding?: string): NekoDigest; digest(encoding?: string): unknown }

  function wrapHash(hostHash: NekoHostHash): NekoDigest {
    const handle: NekoDigest = {
      update(data, encoding) {
        const buffer = unwrapBuffer(data)
        if (buffer) hostHash.updateBuffer(buffer)
        else hostHash.updateString(String(data ?? ''), encoding || 'utf8')
        return handle
      },
      digest(encoding) {
        if (encoding === undefined) return wrapBuffer(hostHash.digestBuffer())
        return hostHash.digestString(encoding)
      }
    }
    return handle
  }

  /** RFC 4122 v4 UUID。 */
  function randomUUID(): string {
    return runtime.crypto().randomUUID()
  }

  function randomBytes(size: number): unknown {
    return wrapBuffer(runtime.crypto().randomBytes(Number(size) || 0))
  }

  function createHash(algorithm: string): NekoDigest {
    return wrapHash(runtime.crypto().createHash(String(algorithm)))
  }

  function createHmac(algorithm: string, key: unknown): NekoDigest {
    const keyBuffer = unwrapBuffer(key)
    const hostKey = keyBuffer || unwrapBuffer(globalThis.Buffer.from(key, 'utf8'))
    return wrapHash(runtime.crypto().createHmac(String(algorithm), hostKey))
  }

  function timingSafeEqual(a: unknown, b: unknown): boolean {
    return runtime.crypto().timingSafeEqual(unwrapBuffer(a), unwrapBuffer(b))
  }

  const crypto = {
    randomUUID,
    randomBytes,
    createHash,
    createHmac,
    timingSafeEqual
  }

  globalThis.__nekoNodeDefine(['crypto', 'node:crypto'], crypto)
})()
