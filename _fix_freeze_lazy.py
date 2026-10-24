# -*- coding: utf-8 -*-
"""
第 7 条优化：把 Snapshot 的三个新属性由「内联 getter」改为 by lazy。

理由：scopedFrozenCount / inScopeFrozen / systemDisabledCount 会被页面
在不同位置多次读取（概览卡、组卡片、LockPage 徽标），内联 getter 每次都
重建 HashSet，既浪费又可能在同一帧内算出不同步的值。Snapshot 是不可变
data class，缓存一次最合理。
"""
import io

P = '/storage/emulated/0/Z-ai工作区/_native/app/src/main/java/com/zimky/coregateUS/core/FreezeData.kt'
s = io.open(P, encoding='utf-8').read()

REPL = [
    ('''        val systemDisabledCount: Int
            get() = actual.size''',
     '''        val systemDisabledCount: Int by lazy { actual.size }'''),

    ('''        val inScopeFrozen: Set<String>
            get() {
                val scope = HashSet<String>()
                for (g in GROUPS) scope.addAll(g.pkgs)
                return actual.keys.filterTo(HashSet()) { it in scope }
            }''',
     '''        val inScopeFrozen: Set<String> by lazy {
            val scope = HashSet<String>()
            for (g in GROUPS) scope.addAll(g.pkgs)
            actual.keys.filterTo(HashSet()) { it in scope }
        }'''),

    ('''        val scopedFrozenCount: Int
            get() = inScopeFrozen.size''',
     '''        val scopedFrozenCount: Int by lazy { inScopeFrozen.size }'''),
]

for old, new in REPL:
    assert old in s, '未命中: ' + old.splitlines()[0]
    s = s.replace(old, new, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('[lazy] 三个属性已改为 by lazy')

# 回读断言
back = io.open(P, encoding='utf-8').read()
assert 'val systemDisabledCount: Int by lazy { actual.size }' in back
assert 'val scopedFrozenCount: Int by lazy { inScopeFrozen.size }' in back
assert 'val inScopeFrozen: Set<String> by lazy {' in back
print('[lazy] 回读校验全部通过')