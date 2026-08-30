#!/usr/bin/env python3
"""把 stonecutter 的节点求值产物洗成可直接落盘的纯 Java 源码。

用途：新建"整文件拆分对"时生成 1.21.1 侧的孪生文件。共享树里带守卫的文件，经 stonecutter
对目标节点求值后落在 versions/<node>/build/generated/stonecutter/ 下，但那还不是干净源码——
守卫标记行（//? if …{、//?} else {、//?}）仍在，失活分支以 /*…*///?} 的注释态保留着。本脚本
删掉标记行与失活分支，只留该节点真正编译的代码，因此产出与编译器所见逐字节一致。

用法：
  python3 tools/extract_evaluated.py <输入文件> <输出文件>

输入必须是求值产物，不能是共享树里手写的源文件——哪个分支活由 stonecutter 按节点参数决定，
本脚本只负责剥离，不做求值。

依赖三条 stonecutter 生成形态的约定：
  - 标记行 = 首个非空白 token 为 //? 或 //~ 的行；
  - 失活分支首行以单星 /* 起始（可带同行代码），终止于以 */ 收尾的行；
  - 启用分支的内容永不被包装，真实块注释一律是 /** javadoc（仓库约定），据此与失活包装区分。
"""

import sys


def clean(lines):
    out = []
    dropping = False          # 正在丢弃失活分支
    for raw in lines:
        stripped = raw.strip()
        # 守卫标记行：整行丢弃
        if stripped.startswith("//?") or stripped.startswith("//~"):
            continue
        if dropping:
            # 失活分支终止行：`*///?}` 形态（可再接 } else {）—— stonecutter 的收尾
            # 标记一定含 `*///?` 子串；或独立成行的 `*/`。
            if "*///?" in raw or stripped.endswith("*/"):
                dropping = False
            continue
        # 失活分支开启：单星 /*（/** 是 javadoc，保留）
        if stripped.startswith("/*") and not stripped.startswith("/**"):
            dropping = True
            # 极端情形：开启与终止同行（/*x*/ 形态）——跳过整行
            if stripped.endswith("*/") and not stripped.endswith("/**/"):
                dropping = False
            continue
        out.append(raw.rstrip("\n"))
    return out


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "r", encoding="utf-8") as f:
        lines = f.readlines()
    cleaned = clean(lines)
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(cleaned) + "\n")
    print(f"{src} -> {dst} ({len(lines)} -> {len(cleaned)} 行)")


if __name__ == "__main__":
    main()
