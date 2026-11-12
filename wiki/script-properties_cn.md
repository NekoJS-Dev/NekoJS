<!-- wiki-page: script-properties; locale: cn -->

> **中文** · [English](script-properties_us)

<a id="wiki-section-1"></a>
# 脚本属性

脚本属性是写在脚本文件开头的特殊注释，用来控制这个文件的加载：先后顺序、是否加载、在哪些模组存在时加载。

<a id="wiki-section-2"></a>
## 写法

每个属性占一行，格式是 `// 名称: 值`：

```javascript
// priority: 100
// modloaded: create, jei
// after: lib/init.js

console.info('hello')
```

属性必须写在文件**最开头**。NekoJS 从第一行往下读，读到第一行不是 `//` 注释、也不是空行的内容就停下。所以：

- 属性之间可以有空行和普通的 `//` 注释。
- `import`、`/* */` 块注释或任何代码之后的属性都不会被读取。

写错位置或拼错属性名（比如 `priorty`）都**不会报错**，属性只是不生效。如果加载顺序不对或者 `disable` 没起作用，先检查这两点。

改了属性之后，重载这类脚本（例如 `/nekojs reload`）就会生效；启动脚本要重启游戏。

<a id="wiki-section-3"></a>
## 所有属性

| 属性 | 值 | 默认 | 作用 |
|---|---|---|---|
| `priority` | 整数 | `0` | 数字越大越先加载 |
| `after` | 逗号分隔的路径 | 无 | 在列出的文件之后加载 |
| `modloaded` | 逗号分隔的模组 ID | 无 | 列出的模组**全部**存在时才加载 |
| `disable` | 任意，可以为空 | 不禁用 | 不加载这个文件 |

<a id="wiki-section-4"></a>
## priority：控制加载顺序

```javascript
// priority: 1000
```

数字越大越先加载，可以是负数。适合用在初始化共享数据的“库”脚本上，让它比其他脚本先运行。

启动脚本按同样的顺序运行，所以 priority 大的启动脚本里的注册也会先发生。

值只能是一个整数。`// priority: 10 先加载` 这样在后面附带说明会让这一行无效，说明请另起一行写。值无效时，日志里会出现 `NEKO-1025` 警告，写明文件和行号，这一行被忽略。

<a id="wiki-section-5"></a>
## after：在指定文件之后加载

```javascript
// after: lib/init.js, lib/constants.js
```

路径的写法：

| 写法 | 含义 |
|---|---|
| `lib/init.js` | 相对于这类脚本的根目录，例如 `server_scripts/lib/init.js` |
| `./helper.js` | 相对于当前文件所在的目录 |
| `lib/*` | `lib/` 目录下的所有文件 |
| `server/lib/init.js` | 带上脚本类型名（`startup`、`server`、`client`、`test`），和 `lib/init.js` 相同 |
| `nekojs:server/lib/init.js` | 同上，加了 `nekojs:` 前缀 |

多个路径用逗号分隔。反斜杠 `\` 会当作 `/` 处理。

**after 只在 priority 相同的文件之间起作用。** 两个文件 priority 不同时，永远是 priority 大的先加载，`after` 会被安静地忽略，日志里也没有提示。所以“写了 `after` 却没效果、日志也没报错”，最常见的原因就是两个文件 priority 不一样。

指向被 `disable` 或 `modloaded` 跳过的文件，同样会被安静地忽略。

路径找不到对应文件时，日志会出现 `NEKO-1004` 警告。几个文件互相 `after` 形成循环时，也会报 `NEKO-1004`，这些文件改回按路径顺序加载。

<a id="wiki-section-6"></a>
## modloaded：模组存在时才加载

```javascript
// modloaded: create, jei
```

只有 `create` 和 `jei` **都**已安装时，这个文件才会加载。适合写某个模组的联动脚本：没装这个模组时脚本直接跳过，不会报错。

这里要写模组 ID，不是模组的显示名称。模组 ID 可以在模组列表里查看。

<a id="wiki-section-7"></a>
## disable：暂时停用

```javascript
// disable:
```

只要文件开头有 `disable` 这一行，这个文件就不会加载。冒号后面写什么都一样：`// disable: false` **也会**停用，可以用来写停用原因。要重新启用，删掉这一行。

<a id="wiki-section-8"></a>
## 加载顺序怎么决定

同一类脚本的加载顺序：

1. 按 `priority` 从大到小排。
2. priority 相同的文件，按 `after` 调整先后。
3. 其余的按文件路径的字母顺序。

简单的用法：用 `priority` 把文件分成几批，用 `after` 在同一批内部排先后。

```javascript
// priority: 500
// modloaded: create
// after: lib/init.js

// 这个文件：
// 1. 比默认 priority 0 的文件先加载
// 2. 只在安装了 Create 时加载
// 3. 如果 lib/init.js 也是 priority 500，就排在它后面

console.info('Create integration initialized')
```

<a id="wiki-section-9"></a>
## 注意事项

- **Python 脚本不支持脚本属性。** `.py` 文件里写 `# priority:` 不会被读取。见 [Python 脚本](python-scripts_cn)。
- **脚本包有自己的依赖顺序。** 脚本包声明的前置包总会先加载，提高 priority 也改变不了。见 [脚本包](script-packs_cn)。
- 插件可以注册新的脚本属性，见 [插件开发](plugin-development_cn)。

另见：[脚本基础](script-basics_cn)、[模块系统](module-system_cn)（用 `require` / `import` 拆分文件）。

<!-- wiki-nav -->

---

[上一篇: 脚本 API 约定](script-api-style_cn) · [目录](Home) · [下一篇: 全局绑定](global-bindings_cn)
