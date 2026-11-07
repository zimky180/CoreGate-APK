#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 gradle-build.log 提炼错误，输出为 GitHub annotations（::error::）。

annotations 可通过公开 API 读取，不需要 actions:read 权限，是当前唯一
稳定可取的日志回传通道。每条消息做转义并截断，避免换行破坏格式。
"""
import os
import re
import sys

MAX_MSG = 900
MAX_ITEMS = 14

ESCAPE = [('%', '%25'), ('\r', '%0D'), ('\n', '%0A')]


def esc(s):
    for a, b in ESCAPE:
        s = s.replace(a, b)
    return s


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else 'ci-logs/gradle-build.log'
    try:
        raw = open(path, 'r', errors='replace').read()
    except Exception as e:
        print('::error::cannot read %s: %s' % (path, e))
        return 0

    lines = raw.splitlines()
    print('log lines = %d, bytes = %d' % (len(lines), len(raw)))

    pat = re.compile(
        r'(^e:|FAILURE|What went wrong|Could not |error:|Unresolved reference|'
        r'Caused by:|A problem occurred|Execution failed|> Task .*FAILED|'
        r'Cannot |Unable to |not found|No such)', re.I)

    seen, uniq = set(), []
    for ln in lines:
        s = ln.strip()
        if not s or not pat.search(s):
            continue
        if s in seen:
            continue
        seen.add(s)
        uniq.append(s)

    print('candidates = %d' % len(uniq))

    # 1) 环境信息头
    head = [ln.strip() for ln in lines[:12] if ln.strip()]
    for ln in head[:6]:
        print('::error::HEAD %s' % esc(ln[:400]))

    # 2) 末尾上下文（Gradle 的 "What went wrong" 一定在末尾）
    tail = [ln.strip() for ln in lines[-120:] if ln.strip()]
    for i in range(0, min(len(tail), 30), 2):
        chunk = ' | '.join(tail[i:i + 2])
        print('::error::TAIL %s' % esc(chunk[:MAX_MSG]))

    # 3) 提炼出的错误行
    for ln in uniq[:MAX_ITEMS]:
        print('::error::%s' % esc(ln[:MAX_MSG]))

    sp = os.environ.get('GITHUB_STEP_SUMMARY')
    if sp:
        try:
            with open(sp, 'a') as f:
                f.write('\n### gradle 失败摘要\n\n```\n')
                f.write('\n'.join(tail[-40:]))
                f.write('\n```\n\n#### 关键错误\n\n```\n')
                f.write('\n'.join(uniq[:40]))
                f.write('\n```\n')
        except Exception as e:
            print('summary write failed: %s' % e)
    return 0


if __name__ == '__main__':
    sys.exit(main())