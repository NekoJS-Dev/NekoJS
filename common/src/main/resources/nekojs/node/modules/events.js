;(function () {
  class EventEmitter {
    constructor() { this._events = Object.create(null) }
    on(name, listener) { (this._events[name] ||= []).push(listener); return this }
    addListener(name, listener) { return this.on(name, listener) }
    once(name, listener) {
      const wrap = (...args) => { this.off(name, wrap); listener(...args) }
      return this.on(name, wrap)
    }
    off(name, listener) { return this.removeListener(name, listener) }
    removeListener(name, listener) {
      const list = this._events[name]
      if (!list) return this
      const i = list.indexOf(listener)
      if (i >= 0) list.splice(i, 1)
      return this
    }
    emit(name, ...args) {
      const list = this._events[name]
      if (!list) return false
      for (const listener of [...list]) listener(...args)
      return true
    }
    listeners(name) { return [...(this._events[name] || [])] }
    removeAllListeners(name) {
      if (name === undefined) this._events = Object.create(null)
      else delete this._events[name]
      return this
    }
  }

  const events = {
    EventEmitter,
    once: (emitter, name) => new Promise(resolve => emitter.once(name, (...args) => resolve(args))),
    on: (emitter, name) => ({ async *[Symbol.asyncIterator]() { while (true) yield await events.once(emitter, name) } })
  }

  globalThis.__nekoNodeDefine(['events', 'node:events'], events)
})()
