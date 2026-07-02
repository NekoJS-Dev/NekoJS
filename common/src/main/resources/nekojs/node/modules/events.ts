;(function () {
  type NekoEventListener = (...args: unknown[]) => void

  class EventEmitter {
    private _events: Record<string | symbol, NekoEventListener[]>
    private _maxListeners: number | undefined

    constructor() {
      this._events = Object.create(null)
      this._maxListeners = undefined
    }

    on(name: string | symbol, listener: NekoEventListener): this { return this.addListener(name, listener) }

    addListener(name: string | symbol, listener: NekoEventListener): this {
      checkListener(listener)
      ;(this._events[name] ||= []).push(listener)
      return this
    }

    prependListener(name: string | symbol, listener: NekoEventListener): this {
      checkListener(listener)
      ;(this._events[name] ||= []).unshift(listener)
      return this
    }

    once(name: string | symbol, listener: NekoEventListener): this {
      checkListener(listener)
      const wrap: NekoEventListener = (...args: unknown[]): void => {
        this.off(name, wrap)
        listener(...args)
      }
      ;(wrap as unknown as Record<string, NekoEventListener>).listener = listener
      return this.on(name, wrap)
    }

    prependOnceListener(name: string | symbol, listener: NekoEventListener): this {
      checkListener(listener)
      const wrap: NekoEventListener = (...args: unknown[]): void => {
        this.off(name, wrap)
        listener(...args)
      }
      ;(wrap as unknown as Record<string, NekoEventListener>).listener = listener
      return this.prependListener(name, wrap)
    }

    off(name: string | symbol, listener: NekoEventListener): this { return this.removeListener(name, listener) }

    removeListener(name: string | symbol, listener: NekoEventListener): this {
      const list = this._events[name]
      if (!list) return this
      const i = list.findIndex(candidate => candidate === listener || (candidate as unknown as Record<string, NekoEventListener>).listener === listener)
      if (i >= 0) list.splice(i, 1)
      if (list.length === 0) delete this._events[name]
      return this
    }

    emit(name: string | symbol, ...args: unknown[]): boolean {
      const list = this._events[name]
      if ((!list || list.length === 0) && name === 'error') {
        const error = args[0]
        if (error instanceof Error) throw error
        throw new Error('Unhandled error.' + (error === undefined ? '' : ' ' + String(error)))
      }
      if (!list || list.length === 0) return false
      for (const listener of [...list]) listener(...args)
      return true
    }

    listeners(name: string | symbol): NekoEventListener[] {
      return (this._events[name] || []).map(listener => (listener as unknown as Record<string, NekoEventListener>).listener || listener)
    }

    rawListeners(name: string | symbol): NekoEventListener[] {
      return [...(this._events[name] || [])]
    }

    listenerCount(name: string | symbol): number {
      return (this._events[name] || []).length
    }

    eventNames(): Array<string | symbol> {
      return Reflect.ownKeys(this._events).filter(name => (this._events[name] || []).length > 0)
    }

    setMaxListeners(value: number): this {
      const max = Number(value)
      if (!Number.isFinite(max) || max < 0) throw new RangeError('n must be a non-negative number')
      this._maxListeners = max
      return this
    }

    getMaxListeners(): number {
      return this._maxListeners === undefined ? EventEmitter.defaultMaxListeners : this._maxListeners
    }

    removeAllListeners(name?: string | symbol): this {
      if (name === undefined) this._events = Object.create(null)
      else delete this._events[name]
      return this
    }

    static defaultMaxListeners: number = 10
    static listenerCount(emitter: EventEmitter, name: string | symbol): number { return emitter.listenerCount(name) }
  }

  function checkListener(listener: NekoEventListener): void {
    if (typeof listener !== 'function') throw new TypeError('listener must be a function')
  }

  async function *on(emitter: EventEmitter, name: string | symbol): AsyncIterableIterator<unknown[]> {
    while (true) yield await oncePromise(emitter, name)
  }

  function oncePromise(emitter: EventEmitter, name: string | symbol): Promise<unknown[]> {
    return new Promise(resolve => emitter.once(name, (...args: unknown[]) => resolve(args)))
  }

  function onIterable(emitter: EventEmitter, name: string | symbol): AsyncIterable<unknown[]> {
    const iterable: AsyncIterable<unknown[]> = { [Symbol.asyncIterator]() { return on(emitter, name) } }
    return iterable
  }

  function listenerCountOf(emitter: EventEmitter, name: string | symbol): number {
    return emitter.listenerCount(name)
  }

  const events = {
    EventEmitter,
    get defaultMaxListeners(): number { return EventEmitter.defaultMaxListeners },
    set defaultMaxListeners(value: number) { EventEmitter.defaultMaxListeners = Number(value) },
    once: oncePromise,
    on: onIterable,
    listenerCount: listenerCountOf
  }

  globalThis.__nekoNodeDefine(['events', 'node:events'], events)
})()
