;(function () {
  class EventEmitter {
    constructor() {
      this._events = Object.create(null)
      this._maxListeners = undefined
    }

    on(name, listener) { return this.addListener(name, listener) }

    addListener(name, listener) {
      checkListener(listener)
      ;(this._events[name] ||= []).push(listener)
      return this
    }

    prependListener(name, listener) {
      checkListener(listener)
      ;(this._events[name] ||= []).unshift(listener)
      return this
    }

    once(name, listener) {
      checkListener(listener)
      const wrap = (...args) => {
        this.off(name, wrap)
        listener(...args)
      }
      wrap.listener = listener
      return this.on(name, wrap)
    }

    prependOnceListener(name, listener) {
      checkListener(listener)
      const wrap = (...args) => {
        this.off(name, wrap)
        listener(...args)
      }
      wrap.listener = listener
      return this.prependListener(name, wrap)
    }

    off(name, listener) { return this.removeListener(name, listener) }

    removeListener(name, listener) {
      const list = this._events[name]
      if (!list) return this
      const i = list.findIndex(candidate => candidate === listener || candidate.listener === listener)
      if (i >= 0) list.splice(i, 1)
      if (list.length === 0) delete this._events[name]
      return this
    }

    emit(name, ...args) {
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

    listeners(name) {
      return (this._events[name] || []).map(listener => listener.listener || listener)
    }

    rawListeners(name) {
      return [...(this._events[name] || [])]
    }

    listenerCount(name) {
      return (this._events[name] || []).length
    }

    eventNames() {
      return Reflect.ownKeys(this._events).filter(name => (this._events[name] || []).length > 0)
    }

    setMaxListeners(value) {
      const max = Number(value)
      if (!Number.isFinite(max) || max < 0) throw new RangeError('n must be a non-negative number')
      this._maxListeners = max
      return this
    }

    getMaxListeners() {
      return this._maxListeners === undefined ? EventEmitter.defaultMaxListeners : this._maxListeners
    }

    removeAllListeners(name) {
      if (name === undefined) this._events = Object.create(null)
      else delete this._events[name]
      return this
    }
  }

  EventEmitter.defaultMaxListeners = 10
  EventEmitter.listenerCount = (emitter, name) => emitter.listenerCount(name)

  function checkListener(listener) {
    if (typeof listener !== 'function') throw new TypeError('listener must be a function')
  }

  async function *on(emitter, name) {
    while (true) yield await events.once(emitter, name)
  }

  const events = {
    EventEmitter,
    get defaultMaxListeners() { return EventEmitter.defaultMaxListeners },
    set defaultMaxListeners(value) { EventEmitter.defaultMaxListeners = Number(value) },
    once: (emitter, name) => new Promise(resolve => emitter.once(name, (...args) => resolve(args))),
    on: (emitter, name) => ({ [Symbol.asyncIterator]: () => on(emitter, name) }),
    listenerCount: (emitter, name) => emitter.listenerCount(name)
  }

  globalThis.__nekoNodeDefine(['events', 'node:events'], events)
})()
