;(function () {
  const runtime = globalThis.__nekoNodeInternal.runtime
  const isTestScript = runtime && String(runtime.scriptType && runtime.scriptType()) === 'TEST'

  interface NekoNodeTestOptions { skip?: boolean | string; todo?: boolean | string }
  type NekoNodeTestCallback = (context: NekoNodeTestContext) => unknown | Promise<unknown>
  interface NekoNodeTestContext {
    name: string
    diagnostic(message: string): void
    test(name: string, fn: NekoNodeTestCallback): unknown
    test(name: string, options: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    skip(message?: string): void
    todo(message?: string): void
  }
  interface NekoNodeTestDescribe {
    (name: string, fn: NekoNodeTestCallback): unknown
    (name: string, options: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    skip(name: string, options?: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    todo(name: string, options?: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    only: NekoNodeTestDescribe
  }
  interface NekoNodeTestRunner {
    (name: string, fn: NekoNodeTestCallback): unknown
    (name: string, options: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    skip(name: string, options?: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    todo(name: string, options?: NekoNodeTestOptions, fn?: NekoNodeTestCallback): unknown
    only: NekoNodeTestRunner
    before(fn: NekoNodeTestCallback): void
    after(fn: NekoNodeTestCallback): void
    beforeEach(fn: NekoNodeTestCallback): void
    afterEach(fn: NekoNodeTestCallback): void
    describe: NekoNodeTestDescribe
    it: NekoNodeTestRunner
    run(): void
    default: NekoNodeTestRunner
  }

  interface NekoNodeTestSuite {
    kind: 'suite'
    name: string
    options: NekoNodeTestOptions
    parent: NekoNodeTestSuite | null
    nodes: NekoNodeTestNode[]
    before: NekoNodeTestCallback[]
    after: NekoNodeTestCallback[]
    beforeEach: NekoNodeTestCallback[]
    afterEach: NekoNodeTestCallback[]
  }
  interface NekoNodeTestCase {
    kind: 'test'
    name: string
    options: NekoNodeTestOptions
    fn: NekoNodeTestCallback | undefined
  }
  type NekoNodeTestNode = NekoNodeTestSuite | NekoNodeTestCase
  interface NekoNodeTestState {
    count: number
    passed: number
    failed: number
    skipped: number
    todo: number
    scheduled: boolean
    running: boolean
    finished: boolean
    root: NekoNodeTestSuite | null
    stack: NekoNodeTestSuite[]
  }
  interface NekoNormalizedArgs {
    name: string
    options: NekoNodeTestOptions
    fn: NekoNodeTestCallback | undefined
  }
  interface NekoMappedValue { get: (k: string) => unknown }
  interface NekoMappedStackLine { path: string | null; line: number; column: number }
  type NekoValueFn = () => unknown
  type NekoVoidCallback = () => void
  type NekoEnqueue = (cb: NekoVoidCallback) => unknown
  type NekoTestExtra = { mapStackLine?: (line: string) => string; formatError?: (error: unknown) => string }

  function createSuite(name: string, options: NekoNodeTestOptions, parent: NekoNodeTestSuite | null): NekoNodeTestSuite {
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

  const state: NekoNodeTestState = globalThis.__nekoNodeTestState || (globalThis.__nekoNodeTestState = {
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

  function ensureTestScripts(): void {
    if (!isTestScript) {
      throw new Error('node:test is only available in test_scripts. Use /nekojs test to run these files.')
    }
  }

  function helper(): unknown {
    return typeof globalThis.Test !== 'undefined' ? globalThis.Test : null
  }

  function info(message: string): void {
    console.info(message)
  }

  function logPass(name: string): void {
    info(`[NekoJS Test][PASS] ${name}`)
  }

  function mapStackLine(line: string): string {
    const match = /^(\s*at\s+.*\()([^()]+\.\w+):(\d+)(?::(\d+))?(\).*)$/.exec(line)
    if (!match || !globalThis.__nekoNodeRuntime || typeof globalThis.__nekoNodeRuntime.mapStackLine !== 'function') {
      return line
    }
    let columnRaw = 1
    if (match[4]) columnRaw = Number(match[4])
    const mapped = normalizeMappedStackLine(globalThis.__nekoNodeRuntime.mapStackLine(match[2], Number(match[3]), columnRaw))
    if (!mapped || !mapped.line) return line
    return `${match[1]}${mapped.path || match[2]}:${mapped.line}${mapped.column ? ':' + mapped.column : ''}${match[5]}`
  }

  function normalizeMappedStackLine(mapped: unknown): NekoMappedStackLine | null {
    if (!mapped) return null
    const path = mappedValue(mapped, 'path')
    const line = mappedValue(mapped, 'line')
    const column = mappedValue(mapped, 'column')
    return { path, line: Number(line) || 0, column: Number(column) || 0 }
  }

  function mappedValue(mapped: unknown, key: string): unknown {
    const value = (mapped as Record<string, unknown>)[key]
    if (typeof value !== 'function') return value
    try {
      return (value as NekoValueFn).call(mapped)
    } catch (_) {
      return typeof (mapped as Record<string, unknown>).get === 'function' ? (mapped as unknown as NekoMappedValue).get(key) : undefined
    }
  }

  function formatError(error: unknown): string {
    const message = error && (error as Error).stack ? (error as Error).stack : (error && (error as Error).message ? (error as Error).message : String(error))
    return String(message).split('\n').map(mapStackLine).join('\n')
  }

  function logFail(name: string, error: unknown): void {
    state.failed++
    info(`[NekoJS Test][FAIL] ${name}: ${formatError(error)}`)
  }

  function logSkip(name: string, reason: string | undefined): void {
    state.skipped++
    info(`[NekoJS Test][SKIP] ${name}${reason ? ': ' + reason : ''}`)
  }

  function logTodo(name: string, reason: string | undefined): void {
    state.todo++
    info(`[NekoJS Test][TODO] ${name}${reason ? ': ' + reason : ''}`)
  }

  function logSection(name: string): void {
    info(`[NekoJS Test][SECTION] ${name}`)
  }

  function normalizeArgs(name: unknown, options: unknown, fn: unknown): NekoNormalizedArgs {
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
      options: (options as NekoNodeTestOptions) || {},
      fn: fn as NekoNodeTestCallback | undefined
    }
  }

  function currentSuite(): NekoNodeTestSuite {
    return state.stack[state.stack.length - 1] || state.root as NekoNodeTestSuite
  }

  function addNode(node: NekoNodeTestNode): NekoNodeTestNode {
    currentSuite().nodes.push(node)
    scheduleRun()
    return node
  }

  function callMaybeAsync(fn: NekoNodeTestCallback, context: NekoNodeTestContext): Promise<unknown> {
    try {
      return Promise.resolve(fn(context))
    } catch (error) {
      return Promise.reject(error)
    }
  }

  function runHooks(list: NekoNodeTestCallback[], context: NekoNodeTestContext): Promise<void> {
    let chain: Promise<void> = Promise.resolve()
    for (const fn of list) {
      chain = chain.then(() => callMaybeAsync(fn, context))
    }
    return chain
  }

  function collectBeforeEach(ancestors: NekoNodeTestSuite[]): NekoNodeTestCallback[] {
    const hooks: NekoNodeTestCallback[] = []
    for (const suite of ancestors) hooks.push(...suite.beforeEach)
    return hooks
  }

  function collectAfterEach(ancestors: NekoNodeTestSuite[]): NekoNodeTestCallback[] {
    const hooks: NekoNodeTestCallback[] = []
    for (let i = ancestors.length - 1; i >= 0; i--) hooks.push(...ancestors[i].afterEach)
    return hooks
  }

  function createContext(name: string, ancestors: NekoNodeTestSuite[]): NekoNodeTestContext {
    const context: NekoNodeTestContext = {
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
    return context
  }

  function createTest(name: string, options: NekoNodeTestOptions, fn: NekoNodeTestCallback | undefined): NekoNodeTestCase {
    return { kind: 'test', name, options: options || {}, fn }
  }

  const test = function test(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    return addNode(createTest(args.name, args.options, args.fn))
  } as NekoNodeTestRunner

  function describe(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    const suite = createSuite(args.name, args.options, currentSuite())
    addNode(suite)

    if (typeof args.fn === 'function' && !suite.options.skip && !suite.options.todo) {
      state.stack.push(suite)
      try {
        (args.fn as NekoNodeTestCallback)(createContext(suite.name, state.stack.slice()))
      } finally {
        state.stack.pop()
      }
    }
    return suite
  } as NekoNodeTestDescribe

  function addHook(kind: 'before' | 'after' | 'beforeEach' | 'afterEach', fn: NekoNodeTestCallback): void {
    ensureTestScripts()
    if (typeof fn !== 'function') throw new TypeError(`node:test ${kind} hook requires a function`)
    currentSuite()[kind].push(fn)
  }

  function reason(options: NekoNodeTestOptions, key: 'skip' | 'todo'): string | undefined {
    return typeof options[key] === 'string' ? options[key] as string : undefined
  }

  async function runTest(node: NekoNodeTestCase, ancestors: NekoNodeTestSuite[]): Promise<void> {
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

  async function runSuite(suite: NekoNodeTestSuite, ancestors: NekoNodeTestSuite[]): Promise<void> {
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

  function printSummary(): void {
    state.finished = true
    info(`[NekoJS Test][SUMMARY] passed=${state.passed} failed=${state.failed} skipped=${state.skipped} todo=${state.todo}`)
  }

  function scheduleRun(): void {
    if (state.scheduled || state.running || state.finished) return
    state.scheduled = true
    const timers = globalThis.__nekoNodeTimers
    const enqueue: NekoEnqueue = timers && typeof timers.setImmediate === 'function' ? timers.setImmediate : fallbackEnqueue
    enqueue(() => {
      state.scheduled = false
      runAll()
    })
  }

  function fallbackEnqueue(cb: NekoVoidCallback): unknown {
    return Promise.resolve().then(cb)
  }

  function runAll(): void {
    ensureTestScripts()
    if (state.running || state.finished) return
    state.running = true
    Promise.resolve(runSuite(state.root as NekoNodeTestSuite, []))
      .then(printSummary, (error: unknown) => {
        logFail('node:test runner', error)
        printSummary()
      })
  }

  test.only = test
  test.skip = function skip(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.skip = args.options.skip || true
    return addNode(createTest(args.name, args.options, args.fn))
  }
  test.todo = function todo(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.todo = args.options.todo || true
    return addNode(createTest(args.name, args.options, args.fn))
  }
  test.before = (fn: NekoNodeTestCallback): void => { addHook('before', fn) }
  test.after = (fn: NekoNodeTestCallback): void => { addHook('after', fn) }
  test.beforeEach = (fn: NekoNodeTestCallback): void => { addHook('beforeEach', fn) }
  test.afterEach = (fn: NekoNodeTestCallback): void => { addHook('afterEach', fn) }
  test.describe = describe
  test.it = test
  test.run = runAll
  ;(test as unknown as NekoTestExtra).mapStackLine = mapStackLine
  ;(test as unknown as NekoTestExtra).formatError = formatError
  test.default = test

  describe.only = describe
  describe.skip = function skip(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.skip = args.options.skip || true
    return describe(args.name, args.options, args.fn)
  }
  describe.todo = function todo(name: unknown, options?: unknown, fn?: unknown): unknown {
    ensureTestScripts()
    const args = normalizeArgs(name, options, fn)
    args.options.todo = args.options.todo || true
    return describe(args.name, args.options, args.fn)
  }

  globalThis.__nekoNodeDefine(['test', 'node:test'], test)
})()
