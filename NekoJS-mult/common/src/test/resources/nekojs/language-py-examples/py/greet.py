# 被 import 的兄弟 .py 模块：顶层定义会被导出，供 `from greet import ...` 使用。
TAG = 'py'


def greet(name):
    return 'hello, ' + name + '!'
