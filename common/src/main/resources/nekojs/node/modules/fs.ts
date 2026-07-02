;(function () {
  const internal = globalThis.__nekoNodeInternal
  const { runtime, callbackResult, promiseResult, encodingFromOptions, recursiveFromOptions, forceFromOptions } = internal
  const { wrapBuffer, unwrapBuffer } = globalThis.__nekoNodeBuffer

  interface NekoStats {
    dev: number; ino: number; mode: number; nlink: number; uid: number; gid: number; rdev: number
    size: number; blksize: number; blocks: number; atimeMs: number; mtimeMs: number; ctimeMs: number; birthtimeMs: number
    isFile(): boolean; isDirectory(): boolean; isSymbolicLink(): boolean; isBlockDevice(): boolean
    isCharacterDevice(): boolean; isFIFO(): boolean; isSocket(): boolean; isOther(): boolean
  }

  function wrapStats(stats: any): NekoStats {
    return {
      dev: Number(stats.dev()),
      ino: Number(stats.ino()),
      mode: Number(stats.mode()),
      nlink: Number(stats.nlink()),
      uid: Number(stats.uid()),
      gid: Number(stats.gid()),
      rdev: Number(stats.rdev()),
      size: Number(stats.size()),
      blksize: Number(stats.blksize()),
      blocks: Number(stats.blocks()),
      atimeMs: Number(stats.atimeMs()),
      mtimeMs: Number(stats.mtimeMs()),
      ctimeMs: Number(stats.ctimeMs()),
      birthtimeMs: Number(stats.birthtimeMs()),
      isFile: (): boolean => stats.isFile(),
      isDirectory: (): boolean => stats.isDirectory(),
      isSymbolicLink: (): boolean => stats.isSymbolicLink(),
      isBlockDevice: (): boolean => stats.isBlockDevice(),
      isCharacterDevice: (): boolean => stats.isCharacterDevice(),
      isFIFO: (): boolean => stats.isFIFO(),
      isSocket: (): boolean => stats.isSocket(),
      isOther: (): boolean => stats.isOther()
    }
  }

  function existsSync(path: unknown): boolean { return runtime.fs().existsSync(String(path)) }
  function accessSync(path: unknown, mode?: unknown): void { runtime.fs().access(String(path), Number(mode) || 0) }
  function readFileSync(path: unknown, options?: unknown): unknown {
    const encoding = encodingFromOptions(options)
    if (encoding) return String(runtime.fs().readFileString(String(path), encoding))
    return wrapBuffer(runtime.fs().readFileBuffer(String(path)))
  }
  function writeFileSync(path: unknown, data: unknown, options?: unknown): void {
    const buffer = unwrapBuffer(data)
    if (buffer) runtime.fs().writeFileBuffer(String(path), buffer)
    else runtime.fs().writeFile(String(path), String(data ?? ''), encodingFromOptions(options) || 'utf8')
  }
  function appendFileSync(path: unknown, data: unknown, options?: unknown): void { runtime.fs().appendFile(String(path), String(data ?? ''), encodingFromOptions(options) || 'utf8') }
  function mkdirSync(path: unknown, options?: unknown): void { runtime.fs().mkdir(String(path), recursiveFromOptions(options)) }
  function rmSync(path: unknown, options?: unknown): void { runtime.fs().rm(String(path), recursiveFromOptions(options), forceFromOptions(options)) }
  function unlinkSync(path: unknown): void { runtime.fs().unlink(String(path)) }
  function readdirSync(path: unknown): string[] { return Array.from(runtime.fs().readdir(String(path))) }
  function statSync(path: unknown): NekoStats { return wrapStats(runtime.fs().stat(String(path))) }
  function lstatSync(path: unknown): NekoStats { return wrapStats(runtime.fs().lstatSync(String(path))) }
  function renameSync(oldPath: unknown, newPath: unknown): void { runtime.fs().rename(String(oldPath), String(newPath)) }
  function copyFileSync(source: unknown, destination: unknown): void { runtime.fs().copyFile(String(source), String(destination)) }
  function realpathSync(path: unknown): string { return String(runtime.fs().realpath(String(path))) }
  function readlinkSync(path: unknown): string { return String(runtime.fs().readlink(String(path))) }

  const fs = {
    F_OK: 0,
    R_OK: 4,
    W_OK: 2,
    X_OK: 1,
    existsSync,
    accessSync,
    readFileSync,
    writeFileSync,
    appendFileSync,
    mkdirSync,
    rmSync,
    unlinkSync,
    readdirSync,
    statSync,
    lstatSync,
    renameSync,
    copyFileSync,
    realpathSync,
    readlinkSync
  }

  fs.readFile = function (path: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object' || typeof optionsOrCallback === 'string'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => readFileSync(path, hasOptions ? optionsOrCallback : undefined))
  }
  fs.writeFile = function (path: unknown, data: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object' || typeof optionsOrCallback === 'string'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => writeFileSync(path, data, hasOptions ? optionsOrCallback : undefined))
  }
  fs.appendFile = function (path: unknown, data: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object' || typeof optionsOrCallback === 'string'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => appendFileSync(path, data, hasOptions ? optionsOrCallback : undefined))
  }
  fs.mkdir = function (path: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => mkdirSync(path, hasOptions ? optionsOrCallback : undefined))
  }
  fs.rm = function (path: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => rmSync(path, hasOptions ? optionsOrCallback : undefined))
  }
  fs.unlink = function (path: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => unlinkSync(path))
  }
  fs.readdir = function (path: unknown, optionsOrCallback?: unknown, callback?: unknown): void {
    const hasOptions = typeof optionsOrCallback === 'object' || typeof optionsOrCallback === 'string'
    const cb = hasOptions ? callback : optionsOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => readdirSync(path))
  }
  fs.stat = function (path: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => statSync(path))
  }
  fs.lstat = function (path: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => lstatSync(path))
  }
  fs.rename = function (oldPath: unknown, newPath: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => renameSync(oldPath, newPath))
  }
  fs.copyFile = function (source: unknown, destination: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => copyFileSync(source, destination))
  }
  fs.realpath = function (path: unknown, callback?: unknown): void {
    callbackResult(typeof callback === 'function' ? callback : undefined, () => realpathSync(path))
  }
  fs.access = function (path: unknown, modeOrCallback?: unknown, callback?: unknown): void {
    const hasMode = typeof modeOrCallback === 'number'
    const cb = hasMode ? callback : modeOrCallback
    callbackResult(typeof cb === 'function' ? cb : undefined, () => accessSync(path, hasMode ? modeOrCallback : undefined))
  }

  function readFile(path: unknown, options?: unknown): Promise<unknown> { return promiseResult(() => readFileSync(path, options)) }
  function writeFile(path: unknown, data: unknown, options?: unknown): Promise<void> { return promiseResult(() => writeFileSync(path, data, options)) }
  function appendFile(path: unknown, data: unknown, options?: unknown): Promise<void> { return promiseResult(() => appendFileSync(path, data, options)) }
  function mkdir(path: unknown, options?: unknown): Promise<void> { return promiseResult(() => mkdirSync(path, options)) }
  function rm(path: unknown, options?: unknown): Promise<void> { return promiseResult(() => rmSync(path, options)) }
  function unlink(path: unknown): Promise<void> { return promiseResult(() => unlinkSync(path)) }
  function readdir(path: unknown): Promise<string[]> { return promiseResult(() => readdirSync(path)) }
  function stat(path: unknown): Promise<NekoStats> { return promiseResult(() => statSync(path)) }
  function lstat(path: unknown): Promise<NekoStats> { return promiseResult(() => lstatSync(path)) }
  function rename(oldPath: unknown, newPath: unknown): Promise<void> { return promiseResult(() => renameSync(oldPath, newPath)) }
  function copyFile(source: unknown, destination: unknown): Promise<void> { return promiseResult(() => copyFileSync(source, destination)) }
  function realpath(path: unknown): Promise<string> { return promiseResult(() => realpathSync(path)) }
  function access(path: unknown, mode?: unknown): Promise<void> { return promiseResult(() => accessSync(path, mode)) }

  const fsPromises = {
    readFile,
    writeFile,
    appendFile,
    mkdir,
    rm,
    unlink,
    readdir,
    stat,
    lstat,
    rename,
    copyFile,
    realpath,
    access
  }

  fs.promises = fsPromises

  globalThis.__nekoNodeDefine(['fs', 'node:fs'], fs)
  globalThis.__nekoNodeDefine(['fs/promises', 'node:fs/promises'], fsPromises)
})()
