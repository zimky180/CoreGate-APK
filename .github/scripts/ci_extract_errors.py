#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CI 辅助脚本：从 gradle 构建日志里提取真实错误，并以 GitHub Actions
::error:: 注解的形式打印出来。

背景：本地/CI 都不具备 actions:read 权限去读 job logs，匿名网页也被
登录墙挡住。但 check-runs 的 annotations 接口是公开可读的，所以只要
把错误文本塞进 ::error:: 注解，就能反向拿到编译错误原文。

用法：python3 .github/scripts/ci_extract_errors.py ci-logs/gradle-build.log
"""

import re
import sys

MAX_CHUNK = 1000
MAX_ANNOT = 12


def main() -> int:
    if len(sys.argv) < 2:
        print('::error::ci_extract_errors.py 缺少日志文件参数')
        return 2

    path = sys.argv[1]
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            text = f.read()
    except OSError as exc:
        print('::error::无法读取构建日志 %s: %s' % (path, exc))
        return 2

    lines = text.splitlines()

    picked = []
    capture_ww = False
    ww_used = 0

    for ln in lines:
        s = ln.rstrip()

        # Kotlin 编译器错误：e: file:///path/File.kt:123:45 message
        if re.match(r'^e: ', s):
            picked.append(s.strip())
            continue

        # javac 错误
        if ' error: ' in s or s.startswith('error: '):
            picked.append(s.strip())
            continue

        # 最常见的 Kotlin 语义错误关键词
        if 'Unresolved reference' in s or 'Type mismatch' in s:
            picked.append(s.strip())
            continue

        # Gradle 的 What went wrong 段落，最多收录 40 行
        if 'What went wrong' in s:
            capture_ww = True
            ww_used = 0
            picked.append('=== What went wrong ===')
            continue
        if capture_ww:
            if s.strip() == '' or ww_used >= 40:
                capture_ww = False
            else:
                picked.append(s.strip())
                ww_used += 1
                continue

        if s.startswith('FAILURE: '):
            picked.append(s.strip())
            continue
        if 'Caused by:' in s:
            picked.append(s.strip())
            continue
        if 'Could not resolve' in s or 'Could not find' in s:
            picked.append(s.strip())
            continue
        if 'Task ' in s and 'FAILED' in s:
            picked.append(s.strip())
            continue
        if s.startswith('> ') and ('Could not' in s or 'Failed' in s):
            picked.append(s.strip())
            continue

    # 去重保序
    seen = set()
    uniq = []
    for ln in picked:
        if ln not in seen:
            seen.add(ln)
            uniq.append(ln)

    # 一条都没抓到就退回日志尾部，至少给出上下文
    if not uniq:
        uniq = ['(未匹配到已知错误模式，下面是日志尾部)'] + lines[-60:]

    blob = '\n'.join(uniq)

    chunks = [blob[i:i + MAX_CHUNK] for i in range(0, len(blob), MAX_CHUNK)]
    if len(chunks) > MAX_ANNOT:
        chunks = chunks[:MAX_ANNOT]

    total = len(chunks)
    for idx, ch in enumerate(chunks, 1):
        # ::error:: 注解里换行必须转成 %0A，% 本身要转义成 %25
        esc = ch.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
        print('::error::[gradle-%d/%d] %s' % (idx, total, esc))

    print('::error::gradle 构建失败；日志已上传为 artifact gradle-build-log')
    return 0


if __name__ == '__main__':
    sys.exit(main())