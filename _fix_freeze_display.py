# -*- coding: utf-8 -*-
"""
修复第 7 条：冻结显示与真机不符（v2，按 FreezeData.kt 真实写法重写）。

真机取证确认的三处根因：
1. /data/adb/CoreGate/freeze.state 不存在 -> [applied] 段为空
   -> effectiveCount = applied ∩ actual = 0 -> 主页误报「当前没有冻结任何服务」。
2. [actual] 来自 `pm list packages -d`，含用户手动停用的包，不是模块冻结的。
3. FreezePage 直接显示 snap.actual.size（系统停用总数），与主页 0 个自相矛盾。

修复：Snapshot 新增 systemDisabledCount / inScopeFrozen / scopedFrozenCount，
页面与主页统一改用 scopedFrozenCount（模块作用域内真正冻结的条目数）。
"""
import io

ROOT = '/storage/emulated/0/Z-ai工作区/_native/app/src/main/java/com/zimky/coregateUS'
FREEZE_DATA = ROOT + '/core/FreezeData.kt'
FREEZE_PAGE = ROOT + '/page/FreezePage.kt'
MAIN_ACT = ROOT + '/MainActivity.kt'


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, s):
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------------------------------------------------------------- 1. FreezeData
s = read(FREEZE_DATA)

OLD = '''        /** 本模块记录了、但系统里没体现出来 —— 说明引擎没生效，必须在界面上暴露 */
        val staleList: List<String>
            get() = applied.keys.filterNot { actual.containsKey(it) }
'''

NEW = '''        /** 本模块记录了、但系统里没体现出来 —— 说明引擎没生效，必须在界面上暴露 */
        val staleList: List<String>
            get() = applied.keys.filterNot { actual.containsKey(it) }

        /**
         * 系统当前被停用的应用总数。
         *
         * 数据来自 `pm list packages -d`，**包含用户自己在系统设置里手动停用的应用**，
         * 不等于本模块冻结的数量 —— 只适合作背景参考，不要直接当「已冻结 N 个」展示。
         */
        val systemDisabledCount: Int
            get() = actual.size

        /**
         * 落在本模块作用域内的冻结包：`actual` 里出现、且属于 [GROUPS] 任一组的包。
         *
         * 用包名清单匹配而不是靠 freeze.state 记录 —— 即使记录被删了
         * （freeze_restore_all 末尾会 rm 掉 freeze.state），只要组内包在系统里
         * 确实处于停用/挂起状态，这里依然数得出来。
         */
        val inScopeFrozen: Set<String>
            get() {
                val scope = HashSet<String>()
                for (g in GROUPS) scope.addAll(g.pkgs)
                return actual.keys.filterTo(HashSet()) { it in scope }
            }

        /**
         * 界面上真正该给用户看的「已冻结」数字：本模块作用域内、确实处于冻结态的条目数。
         *
         * 它既不像 [effectiveCount] 那样依赖 freeze.state（记录丢了就归零，
         * 主页因此误报「没有冻结」），也不像 [systemDisabledCount] 那样
         * 把用户手动停用的无关包也算进来。
         */
        val scopedFrozenCount: Int
            get() = inScopeFrozen.size
'''

assert OLD in s, 'FreezeData 的 staleList 块未找到'
s = s.replace(OLD, NEW, 1)
write(FREEZE_DATA, s)
assert 'scopedFrozenCount' in read(FREEZE_DATA)
print('[1] FreezeData.Snapshot 新增三个属性 —— OK')

# ---------------------------------------------------------------- 2. FreezePage
s = read(FREEZE_PAGE)

OLD_FP = 'text = "${snap.actual.size} 个"'
if OLD_FP in s:
    s = s.replace(OLD_FP, 'text = "${snap.scopedFrozenCount} 个"', 1)
    print('[2] 概览卡主数字 -> scopedFrozenCount —— OK')
else:
    print('[2] !! 未命中 snap.actual.size')

OLD_NOTE = '以上为系统真实状态，不是本页勾选结果。'
if OLD_NOTE in s:
    NEW_NOTE = ('本模块冻结 ${snap.scopedFrozenCount} 个，系统另有 '
                '${snap.systemDisabledCount} 个应用被停用（含你手动停用的），不计入上数。')
    s = s.replace(OLD_NOTE, NEW_NOTE, 1)
    print('[2] 说明文案改为双口径 —— OK')
else:
    print('[2] 说明文案未命中，跳过')

write(FREEZE_PAGE, s)
print('[2] 回读:', 'scopedFrozenCount' in read(FREEZE_PAGE))

# ---------------------------------------------------------------- 3. MainActivity
s = read(MAIN_ACT)
OLD_MA = 'frozenCount = snap.effectiveCount'
if OLD_MA in s:
    s = s.replace(OLD_MA, 'frozenCount = snap.scopedFrozenCount', 1)
    write(MAIN_ACT, s)
    print('[3] MainActivity.frozenCount -> scopedFrozenCount —— OK')
else:
    print('[3] !! 未命中 frozenCount 赋值')

print('==== 全部完成')