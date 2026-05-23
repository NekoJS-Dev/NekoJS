;(function () {
  const internal = globalThis.__nekoNodeInternal
  const { runtime, callbackResult, promiseResult, encodingFromOptions, recursiveFromOptions, forceFromOptions } = internal
  const { wrapBuffer, unwrapBuffer } = globalThis.__nekoNodeBuffer

  function wrapStats(stats) {
    return {
      size: Number(stats.size()),
      mtimeMs: Number(stats.mtimeMs()),
      ctimeMs: Number(stats.ctimeMs()),
      atimeMs: Number(stats.atimeMs()),
      birthtimeMs: Number(stats.birthtimeMs()),
      isFile: () => stats.isFile(),
      isDirectory: () => stats.isDirectory(),
      isSymbolicLink: () => stats.isSymbolicLink(),
      isOther: () => stats.isOther()
    }
  }

  const fs = {
    F_OK: 0,
    R_OK: 4,
    W_OK: 2,
    X_OK: 1,
    existsSync(path) { return runtime.fs().existsSync(String(path)) },
    accessSync(path, mode) { runtime.fs().stat(String(path)) },
    readFileSync(path, options) {
      const encoding = encodingFromOptions(options)
      return encoding ? String(runtime.fs().readFileString(String(path), encoding)) : wrapBuffer(runtime.fs().readFileBuffer(String(path)))
    },
    writeFileSync(path, data, options) {
      const buffer = unwrapBuffer(data)
      if (buffer) runtime.fs().writeFileBuffer(String(path), buffer)
      else runtime.fs().writeFile(String(path), String(data ?? ''), encodingFromOptions(options) || 'utf8')
    },
    appendFileSync(path, data, options) { runtime.fs().appendFile(String(path), String(data ?? ''), encodingFromOptions(options) || 'utf8') },
    mkdirSync(path, options) { runtime.fs().mkdir(String(path), recursiveFromOptions(options)) },
    rmSync(path, options) { runtime.fs().rm(String(path), recursiveFromOptions(options), forceFromOptions(options)) },
    unlinkSync(path) { runtime.fs().unlink(String(path)) },
    readdirSync(path) { return Array.from(runtime.fs().readdir(String(path))) },
    statSync(path) { return wrapStats(runtime.fs().stat(String(path))) },
    lstatSync(path) { return wrapStats(runtime.fs().lstat(String(path))) },
    renameSync(oldPath, newPath) { runtime.fs().rename(String(oldPath), String(newPath)) },
    copyFileSync(source, destination) { runtime.fs().copyFile(String(source), String(destination)) },
    realpathSync(path) { return String(runtime.fs().realpath(String(path))) },
    readlinkSync(path) { return String(runtime.fs().readlink(String(path))) }
  }

  for (const name of ['readFile', 'writeFile', 'appendFile', 'mkdir', 'rm', 'unlink', 'readdir', 'stat', 'lstat', 'rename', 'copyFile', 'realpath', 'access']) {
    fs[name] = function (...args) {
      const callback = args.at(-1)
      callbackResult(typeof callback === 'function' ? callback : undefined, () => fs[name + 'Sync'](...args.slice(0, typeof callback === 'function' ? -1 : undefined)))
    }
  }

  fs.promises = {
    readFile: (path, options) => promiseResult(() => fs.readFileSync(path, options)),
    writeFile: (path, data, options) => promiseResult(() => fs.writeFileSync(path, data, options)),
    appendFile: (path, data, options) => promiseResult(() => fs.appendFileSync(path, data, options)),
    mkdir: (path, options) => promiseResult(() => fs.mkdirSync(path, options)),
    rm: (path, options) => promiseResult(() => fs.rmSync(path, options)),
    unlink: path => promiseResult(() => fs.unlinkSync(path)),
    readdir: path => promiseResult(() => fs.readdirSync(path)),
    stat: path => promiseResult(() => fs.statSync(path)),
    lstat: path => promiseResult(() => fs.lstatSync(path)),
    rename: (oldPath, newPath) => promiseResult(() => fs.renameSync(oldPath, newPath)),
    copyFile: (source, destination) => promiseResult(() => fs.copyFileSync(source, destination)),
    realpath: path => promiseResult(() => fs.realpathSync(path)),
    access: (path, mode) => promiseResult(() => fs.accessSync(path, mode))
  }

  globalThis.__nekoNodeDefine(['fs', 'node:fs'], fs)
  globalThis.__nekoNodeDefine(['fs/promises', 'node:fs/promises'], fs.promises)
})()
