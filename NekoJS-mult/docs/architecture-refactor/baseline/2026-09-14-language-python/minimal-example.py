# NekoJS Python 最小示例（工单 13）。
# 只用已通过 gate 的 Python 子集：缩进、def/class、注释、import、f-string、列表推导。
# 该文件被 minimal-example-entry.mjs 真实 import，因此示例与实现漂移即测试失败。


def greet(name):
    # 注释行不产生语句，也不影响 source map
    return "hello " + name


class Counter:
    def __init__(self, start=0):
        self.value = start

    def add(self, delta):
        self.value += delta
        return self.value


def squares(n):
    return [i * i for i in range(1, n + 1)]


def boom(n):
    # 故意抛错：诊断材料用它演示「执行期错误回到原始 .py 行」
    if n > 0:
        raise ValueError("python-example-boom")
    return 0
