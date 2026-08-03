#!/usr/bin/env python3
"""目录树输出工具 —— 输入路径，输出树形结构的目录和文件。"""

import argparse
import os
import sys
from pathlib import Path


# 树形线条字符
LINE_VERTICAL = "│   "
LINE_BRANCH   = "├── "
LINE_CORNER   = "└── "
LINE_SPACE    = "    "


def build_tree(path: Path, prefix: str = "", max_depth: int = -1, include_hidden: bool = False) -> str:
    """递归构建目录树字符串。

    参数:
        path: 起始路径
        prefix: 当前行的前缀（用于递归缩进）
        max_depth: 最大深度，-1 表示无限
        include_hidden: 是否包含隐藏文件/目录（以 . 开头）
    """
    if max_depth == 0:
        return ""

    try:
        entries = sorted(path.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
    except PermissionError:
        return f"{prefix}{LINE_CORNER}[权限不足]\n"
    except OSError as e:
        return f"{prefix}{LINE_CORNER}[错误: {e}]\n"

    if not include_hidden:
        entries = [e for e in entries if not e.name.startswith(".")]

    lines = []
    for i, entry in enumerate(entries):
        is_last = (i == len(entries) - 1)
        connector = LINE_CORNER if is_last else LINE_BRANCH
        child_prefix = prefix + (LINE_SPACE if is_last else LINE_VERTICAL)

        lines.append(f"{prefix}{connector}{entry.name}")

        if entry.is_dir():
            subtree = build_tree(
                entry,
                prefix=child_prefix,
                max_depth=max_depth - 1 if max_depth > 0 else -1,
                include_hidden=include_hidden,
            )
            if subtree:
                lines.append(subtree)

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="输出目录树结构（包含文件）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python tree.py                  # 当前目录
  python tree.py /some/path       # 指定路径
  python tree.py . -d 2           # 限制深度为 2
  python tree.py . -a             # 包含隐藏文件
  python tree.py . -d 1 -a        # 组合使用
        """.strip(),
    )
    parser.add_argument(
        "path", nargs="?", default=".",
        help="要展示的目录路径（默认: 当前目录）",
    )
    parser.add_argument(
        "-d", "--max-depth", type=int, default=-1,
        help="最大递归深度（默认: 无限）",
    )
    parser.add_argument(
        "-a", "--all", action="store_true", dest="include_hidden",
        help="包含隐藏文件和目录（以 . 开头）",
    )
    args = parser.parse_args()

    root = Path(args.path).resolve()
    if not root.exists():
        print(f"错误: 路径不存在 —— {root}", file=sys.stderr)
        sys.exit(1)
    if not root.is_dir():
        print(f"错误: 路径不是目录 —— {root}", file=sys.stderr)
        sys.exit(1)

    # 打印根目录名
    print(root.name)
    tree = build_tree(root, max_depth=args.max_depth, include_hidden=args.include_hidden)
    if tree:
        print(tree)

    # 如果是在终端中交互运行（非管道），暂停等待用户按键，防止窗口一闪而过
    if sys.stdout.isatty():
        input("\n按回车键退出...")


if __name__ == "__main__":
    main()
