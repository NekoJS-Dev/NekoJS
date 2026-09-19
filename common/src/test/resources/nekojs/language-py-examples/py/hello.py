# 最小 Python 示例（NekoJS Python 子集 -> JS，纯 Java 转译，无外部运行时）。
# 运行：把本目录两个文件放入 server_scripts/ 任一子目录，入口被加载后 value 即结果。
# 说明：顶层有 def/赋值时模块是 ESM（可被其它 .py/.mjs import）；去掉它们就是普通 CJS 脚本。
from nekojs import *   # 仅给 IDE/pyright 用的类型桩入口；转译时被剥离，不影响 source map

from greet import greet, TAG


def describe(name):
    # 缩进结构、注释、elif 与 f-string 都由转译器保留
    if name == '':
        return 'nobody'
    elif name == TAG:
        return 'the tag itself'
    else:
        return greet(name)


value = describe('neko')
tag = TAG
