#!/usr/bin/env python3
"""从 stonecutter 节点求值产物提取"干净"的已求值源码（DEVEX-ROADMAP 档 1/档 2 整文件拆分的操作法）。

背景：非 active 节点的求值产物在 versions/<node>/build/generated/stonecutter/ 下，
其中守卫标记行（//? if …{、//?} else {、//?}）仍在，失活分支以 /*…*///?} 注释态存在。
本脚本把这种形态清洗为可直接落盘的纯源码：删标记行、删失活分支（含包装符）、
保留活代码（含真实 javadoc——以 /** 开头，与失活包装的单星 /* 区分）。

用法：
  python3 tools/extract_evaluated.py <输入文件> <输出文件> [--strip-args]

约束（与 stonecutter 生成形态对齐）：
  - 标记行 = 首个非空白 token 为 //? 的行（含 //? if/} else/}/else 与 //~）；
  - 失活分支首行以单星 /* 起始（可带同行代码），终止于以 */ 收尾的行；
  - 启用分支内容永不被包装；真实块注释均为 /** javadoc（仓库约定）。
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
