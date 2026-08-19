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
      update(data: unknown, encoding?: string): NekoDigest {
        const buffer = unwrapBuffer(data)
        if (buffer) hostHash.updateBuffer(buffer)
        else hostHash.updateString(String(data ?? ''), encoding || 'utf8')
        return handle
      },
      digest(encoding?: string): unknown {
        if (encoding === undefined) return wrapBuffer(hostHash.digestBuffer())
        return hostHash.digestString(encoding)
      }
    }
    return handle
  }

  const crypto = {
    /** RFC 4122 v4 UUID。 */
    randomUUID(): string {
      return runtime.crypto().randomUUID()
    },
    randomBytes(size: number): unknown {
      return wrapBuffer(runtime.crypto().randomBytes(Number(size) || 0))
    },
    createHash(algorithm: string): NekoDigest {
      return wrapHash(runtime.crypto().createHash(String(algorithm)))
    },
    createHmac(algorithm: string, key: unknown): NekoDigest {
      const keyBuffer = unwrapBuffer(key)
      const hostKey = keyBuffer || unwrapBuffer(globalThis.Buffer.from(key, 'utf8'))
      return wrapHash(runtime.crypto().createHmac(String(algorithm), hostKey))
    },
    timingSafeEqual(a: unknown, b: unknown): boolean {
      return runtime.crypto().timingSafeEqual(unwrapBuffer(a), unwrapBuffer(b))
    }
  }

  globalThis.__nekoNodeDefine(['crypto', 'node:crypto'], crypto)
})()
