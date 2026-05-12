;(function () {
  const runtime = globalThis.__nekoNodeInternal.runtime
  const isTestScript = runtime && String(runtime.scriptType && runtime.scriptType()) === 'TEST'

  function createSuite(name, options, parent) {
    return {
      kind: 'suite',
      name,
      options: options || {},
      parent,
      nodes: [],
      before: [],
      after: [],
      beforeEach: [],
      afterEach: []
    }
  }

  const state = globalThis.__nekoNodeTestState || (globalThis.__nekoNodeTestState = {
    count: 0,
    passed: 0,
    failed: 0,
    skipped: 0,
    todo: 0,
    scheduled: false,
    running: false,
    finished: false,
    root: null,
    stack: []
  })

  if (!state.root) {
    state.root = createSuite('root', {}, null)
    state.stack = [state.root]
  }

  function ensureTestScripts() {
    if (!isTestScript) {
      throw new Error('node:test is only available in test_scripts. Use /nekojs test to run these files.')
    }
  }

  function helper() {
    return typeof globalThis.Test !== 'undefined' ? globalThis.Test : null
  }

  function info(message) {
    console.info(message)
  }

  function logPass(name) {
    const test = helper()
    if (test && typeof test.pass === 'function') test.pass(name)
    else info(`[NekoJS Test][PASS] ${name}`)
  }

  function logFail(name, error) {
    state.failed++
    const message = error && error.stack ? error.stack : (error && error.message ? error.message : String(error))
    info(`[NekoJS Test][FAIL] ${name}: ${message}`)
  }

  function logSkip(name, reason) {
    state.skipped++
    info(`[NekoJS Test][SKIP] ${name}${reason ? ': ' + reason : ''}`)
  }

  function logTodo(name, reason) {
    state.todo++
    info(`[NekoJS Test][TODO] ${name}${reason ? ': ' + reason : ''}`)
  }

  function logSection(name) {
    const test = helper()
    if (test && typeof test.section === 'function') test.section(name)
    else info(`[NekoJS Test][SECTION] ${name}`)
  }

  function normalizeArgs(name, options, fn) {
    if (typeof name === 'function') {
      fn = name
      options = undefined
      name = undefined
    } else if (typeof options === 'function') {
      fn = options
      options = undefined
    }
    if (typeof name === 'object' && name !== null) {
      options = name
      name = undefined
    }
    return {
      name: String(name || `test ${++state.count}`),
      options: options || {},
      fn
    }
  }

  function currentSuite() {
    return state.stack[state.stack.length - 1] || state.root
  }

  function addNode(node) {
    currentSuite().nodes.push(node)
    scheduleRun()
    return node
  }

  function callMaybeAsync(fn, context) {
    try {
      return Promise.resolve(fn(context))
    } catch (error) {
      return Promise.reject(error)
    }
  }

  function runHooks(list, context) {
    let chain = Promise.resolve()
    for (const fn of list) {
      chain = chain.then(() => callMaybeAsync(fn, context))
    }
    return chain
  }

  function collectBeforeEach(ancestors) {
    const hooks = []
    for (const suite of ancestors) hooks.push(...suite.beforeEach)
    return hooks
  }

  function collectAfterEach(ancestors) {
    const hooks = []
    for (let i = ancestors.length - 1; i >= 0; i--) hooks.push(...ancestors[i].afterEach)
    return hooks
  }

  function createContext(name, ancestors) {
    return {
      name,
      diagnostic(message) { info(`[NekoJS Test][DIAG] ${message}`) },
      test(subName, options, fn) {
        const args = normalizeArgs(subName, options, fn)
        const node = createTest(args.name, args.options, args.fn)
        return runTest(node, ancestors)
      },
      skip(message) { logSkip(name, message) },
      todo(message) { logTodo(name, message) }
    }
  }

  function createTest(name, options, fn) {
    return { kind: 'test', name, options: options || {}, fn }
  }

  function test(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    return addNode(createTest(args.name, args.options, args.fn))
  }

  function describe(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    const suite = createSuite(args.name, args.options, currentSuite())
    addNode(suite)

    if (typeof args.fn === 'function' && !suite.options.skip && !suite.options.todo) {
      state.stack.push(suite)
      try {
        args.fn(createContext(suite.name, state.stack.slice()))
      } finally {
        state.stack.pop()
      }
    }
    return suite
  }

  function addHook(kind, fn) {
    ensureTestScripts()
    if (typeof fn !== 'function') throw new TypeError(`node:test ${kind} hook requires a function`)
    currentSuite()[kind].push(fn)
  }

  function reason(options, key) {
    return typeof options[key] === 'string' ? options[key] : undefined
  }

  async function runTest(node, ancestors) {
    if (node.options.skip) {
      logSkip(node.name, reason(node.options, 'skip'))
      return
    }
    if (node.options.todo) {
      logTodo(node.name, reason(node.options, 'todo'))
      return
    }
    if (typeof node.fn !== 'function') {
      logFail(node.name, new TypeError('node:test requires a test callback'))
      return
    }

    logSection(node.name)
    const context = createContext(node.name, ancestors)
    try {
      await runHooks(collectBeforeEach(ancestors), context)
      await callMaybeAsync(node.fn, context)
      await runHooks(collectAfterEach(ancestors), context)
      state.passed++
      logPass(node.name)
    } catch (error) {
      try {
        await runHooks(collectAfterEach(ancestors), context)
      } catch (afterError) {
        logFail(`${node.name} afterEach`, afterError)
      }
      logFail(node.name, error)
    }
  }

  async function runSuite(suite, ancestors) {
    if (suite.options.skip) {
      if (suite !== state.root) logSkip(suite.name, reason(suite.options, 'skip'))
      return
    }
    if (suite.options.todo) {
      if (suite !== state.root) logTodo(suite.name, reason(suite.options, 'todo'))
      return
    }

    const nextAncestors = ancestors.concat(suite)
    const context = createContext(suite.name, nextAncestors)
    if (suite !== state.root) logSection(suite.name)

    try {
      await runHooks(suite.before, context)
      for (const node of suite.nodes) {
        if (node.kind === 'suite') await runSuite(node, nextAncestors)
        else await runTest(node, nextAncestors)
      }
    } catch (error) {
      logFail(suite.name, error)
    } finally {
      try {
        await runHooks(suite.after.slice().reverse(), context)
      } catch (error) {
        logFail(`${suite.name} after`, error)
      }
    }
  }

  function printSummary() {
    state.finished = true
    info(`[NekoJS Test][SUMMARY] passed=${state.passed} failed=${state.failed} skipped=${state.skipped} todo=${state.todo}`)
  }

  function scheduleRun() {
    if (state.scheduled || state.running || state.finished) return
    state.scheduled = true
    const timers = globalThis.__nekoNodeTimers
    const enqueue = timers && typeof timers.setImmediate === 'function' ? timers.setImmediate : cb => Promise.resolve().then(cb)
    enqueue(() => {
      state.scheduled = false
      runAll()
    })
  }

  function runAll() {
    ensureTestScripts()
    if (state.running || state.finished) return
    state.running = true
    Promise.resolve(runSuite(state.root, []))
      .then(printSummary, error => {
        logFail('node:test runner', error)
        printSummary()
      })
  }

  test.only = test
  test.skip = function skip(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.skip = args.options.skip || true
    return addNode(createTest(args.name, args.options, args.fn))
  }
  test.todo = function todo(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.todo = args.options.todo || true
    return addNode(createTest(args.name, args.options, args.fn))
  }
  test.before = fn => addHook('before', fn)
  test.after = fn => addHook('after', fn)
  test.beforeEach = fn => addHook('beforeEach', fn)
  test.afterEach = fn => addHook('afterEach', fn)
  test.describe = describe
  test.it = test
  test.run = runAll
  test.default = test

  describe.only = describe
  describe.skip = function skip(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.skip = args.options.skip || true
    return describe(args.name, args.options, args.fn)
  }
  describe.todo = function todo(name, options, fn) {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.todo = args.options.todo || true
    return describe(args.name, args.options, args.fn)
  }

  globalThis.__nekoNodeDefine(['test', 'node:test'], test)
})()
