#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CI -> 本地 的日志回传通道（issue comment 版）。

背景：本仓库 PAT 缺 actions:read，导致
  GET /actions/jobs/{id}/logs        -> 401
  GET /actions/artifacts/{id}/zip    -> 401
annotations 又只报 "Process completed with exit code 1"，拿不到 Kotlin 错误原文。

方案：用 Actions 内置 GITHUB_TOKEN（自带 issues:write + contents:read），
把 gradle 日志分片写进一个固定 issue 的评论里。
本地用 PAT 的 repo 权限 GET /issues/{n}/comments 即可读回全文，
不依赖任何第三方服务，也不需要在仓库里存明文 PAT。

用法（CI）：
  python3 .github/scripts/report_log_to_issue.py ci-logs/gradle-build.log
环境变量：
  GITHUB_TOKEN     内置 token（secrets.GITHUB_TOKEN）
  GITHUB_REPOSITORY owner/repo
  GITHUB_RUN_ID    运行号
输出：stdout 打印 ISSUE_NUMBER=<n>
"""
import json, os, sys, urllib.request, urllib.error

CHUNK = 55000          # 单条评论正文上限约 65536，留余量
MAX_CHUNKS = 8


def api(url, token, method='GET', payload=None):
    data = None
    headers = {
        'Authorization': 'Bearer ' + token,
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'CoreGate-CI',
        'X-GitHub-Api-Version': '2022-11-28',
    }
    if payload is not None:
        data = json.dumps(payload).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    r = urllib.request.urlopen(req, timeout=60)
    body = r.read().decode('utf-8')
    return json.loads(body) if body.strip() else {}


def main():
    log_path = sys.argv[1] if len(sys.argv) > 1 else 'ci-logs/gradle-build.log'
    token = os.environ.get('GITHUB_TOKEN', '').strip()
    repo = os.environ.get('GITHUB_REPOSITORY', '').strip()
    run = os.environ.get('GITHUB_RUN_ID', '0')
    sha = os.environ.get('GITHUB_SHA', '')[:8]

    if not token or not repo:
        print('missing GITHUB_TOKEN / GITHUB_REPOSITORY, skip')
        return 0
    if not os.path.exists(log_path):
        print('log not found: %s' % log_path)
        return 0

    base = 'https://api.github.com/repos/' + repo
    text = open(log_path, encoding='utf-8', errors='replace').read()

    # 1) 找或建一个固定的回传 issue
    marker = 'CI-LOG-CHANNEL'
    issue_no = None
    try:
        issues = api(base + '/issues?state=all&per_page=100&sort=created&direction=desc',
                     token)
        for it in issues:
            if it.get('title') == '[ci] ' + marker and 'pull_request' not in it:
                issue_no = it['number']
                break
        if issue_no is None:
            d = api(base + '/issues', token, 'POST', {
                'title': '[ci] ' + marker,
                'body': '该 issue 由 CI 自动创建，用于回传构建日志，请勿关闭。',
            })
            issue_no = d['number']
        print('ISSUE_NUMBER=%d' % issue_no)
    except urllib.error.HTTPError as e:
        print('issue ensure failed: %s %s' % (e.code, e.read()[:200]))
        return 0
    except Exception as e:
        print('issue ensure error: %r' % (e,))
        return 0

    # 2) 分片发表评论
    total = max(1, (len(text) + CHUNK - 1) // CHUNK)
    total = min(total, MAX_CHUNKS)
    for i in range(total):
        part = text[i * CHUNK:(i + 1) * CHUNK]
        head = ('### run %s @%s  gradle log (%d/%d)\n\n' % (run, sha, i + 1, total))
        body = head + '```\n' + part + '\n```'
        try:
            api(base + '/issues/%d/comments' % issue_no, token, 'POST', {'body': body})
            print('comment %d/%d ok' % (i + 1, total))
        except urllib.error.HTTPError as e:
            print('comment %d failed: %s %s' % (i + 1, e.code, e.read()[:200]))
        except Exception as e:
            print('comment %d error: %r' % (i + 1, e))

    return 0


if __name__ == '__main__':
    sys.exit(main())