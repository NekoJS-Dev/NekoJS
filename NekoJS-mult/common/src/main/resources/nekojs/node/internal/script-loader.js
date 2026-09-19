;(function () {
  const host = globalThis.__nekoScriptModuleLoaderHost

  function requireHost() {
    if (!host) throw new Error('NekoJS script loader host is unavailable.')
    return host
  }

  function createModule(filename) {
    return { id: filename, filename, exports: {}, loaded: false }
  }

  // Graal compiles the new Function body with a synthesized header ("function anonymous(exports,…" +
  // "\n) {\n"), so module code line N is reported as N + bodyLineOffset. Derive the offset from a
  // probe rather than hardcoding it, and hand it to the host so runtime diagnostics map a guest
  // location back onto the prepared module before resolving the authored position.
  function bodyLineOffset() {
    const text = new Function('exports', 'require', 'module', '__filename', '__dirname', 'return 0').toString()
    const brace = text.indexOf('{')
    return brace < 0 ? 0 : text.slice(0, brace).split('\n').length
  }

  function executeModule(module, requireFn, resolveFn, filename, dirname, code) {
    const localRequire = function require(id) {
      return requireFn(String(id))
    }
    localRequire.resolve = function resolve(id) {
      return resolveFn(String(id))
    }
    const source = `${code}\n//# sourceURL=${filename}`
    const fn = new Function('exports', 'require', 'module', '__filename', '__dirname', source)
    fn(module.exports, localRequire, module, filename, dirname)
  }

  function resolveSpecial(specifier) {
    const resolved = globalThis.__nekoNodeResolve(String(specifier))
    if (resolved === globalThis.__nekoNodeNoModule) {
      throw new Error(`Cannot resolve module: ${specifier}`)
    }
    return resolved
  }

  requireHost().configure(executeModule, createModule, resolveSpecial, JSON.parse.bind(JSON), bodyLineOffset())

  globalThis.__nekoNativeImport = function nativeImport(parentPath, specifier) {
    return Promise.resolve(requireHost().nativeImportAsync(String(parentPath), String(specifier)))
  }

  globalThis.__nekoScriptLoader = {
    loadEntry(entryPath) {
      return requireHost().loadEntry(String(entryPath))
    },
    loadEntryAsync(entryPath) {
      return Promise.resolve(requireHost().loadEntryAsync(String(entryPath)))
    },
    requireFrom(parentPath, specifier) {
      return requireHost().requireFrom(String(parentPath), String(specifier))
    },
    clearCache() {
      requireHost().clearCache()
    },
    clearRuntimeCache() {
      requireHost().clearRuntimeCache()
    },
    invalidateAffectedModules(modulePath) {
      requireHost().invalidateAffectedModules(String(modulePath))
    },
    invalidateModuleTree(modulePath) {
      requireHost().invalidateModuleTree(String(modulePath))
    },
    affectedEntries(modulePath) {
      return requireHost().affectedEntries(String(modulePath))
    }
  }
})()
