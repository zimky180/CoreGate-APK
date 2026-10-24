# -*- coding: utf-8 -*-
"""
修复第 7 条：冻结显示与真机不符。

根因（经真机取证）：
  1) 设备 /data/adb/CoreGate/freeze.state 不存在，--freeze-status 的 [applied]
     段因此为空，MainActivity 的 frozenCount = effectiveCount = 0，
     主页显示「没有冻结」。
  2) 但 [actual] 段有 70+ 项（pm list packages -d 的真实输出），其中既有本模块
     冻结的包，也有用户自己手动停用的包（com.lemon.lv / com.houvven.guise 等）。
  3) FreezePage 概览卡直接显示 snap.actual.size，会让用户以为这 70+ 项全是
     模块冻结的 —— 同样是误导。

修法：把「系统已停用总数」和「落在本模块各组清单内的已停用数」分开算，
      再叠加 applied 记录，得到三个互不混淆的指标。
"""
import io, sys

BASE = "/storage/emulated/0/Z-ai工作区/_native/app/src/main/java/com/zimky/coregateUS"
path = BASE + "/core/FreezeData.kt"

s = io.open(path, encoding="utf-8").read()

old = '''    ) {
        /** 本模块冻结中、且系统里确实已处于冻结态的数量（真正生效的证据） */
        val effectiveCount: Int
            get() = applied.keys.count { actual.containsKey(it) }

        /** 本模块记录了、但系统里没体现出来 —— 说明引擎没生效，必须在界面上暴露 */
        val staleList: List<String>
            get() = applied.keys.filterNot { actual.containsKey(it) }

        fun isGroupOn(key: String): Boolean = groups[key] == true
    }'''

new = '''    ) {
        /**
         * 本模块冻结中、且系统里确实已处于冻结态的数量（真正生效的证据）。
         *
         * 注意：这里只看 [applied]，即「本模块记录在案并冻结成功」的包。
         * 若用户在系统设置里自己停用了某个包，那不在本模块的功劳簿上，
         * 不能算进来 —— 否则会把用户的手动操作冒领成本模块的效果。
         */
        val effectiveCount: Int
            get() = applied.keys.count { actual.containsKey(it) }

        /** 本模块记录了、但系统里没体现出来 —— 说明引擎没生效，必须在界面上暴露 */
        val staleList: List<String>
            get() = applied.keys.filterNot { actual.containsKey(it) }

        /**
         * 系统里当前处于停用/挂起态的总数（含用户自己手动停用的）。
         *
         * 为什么单独列一个：真机上这个数往往远大于 [effectiveCount]，
         * 因为用户可能自己停用过一堆应用。把它和 本模块冻结数 混为一谈，
         * 就会出现「冻结页说 72 个、主页说 0 个」的自相矛盾。
         */
        val systemDisabledCount: Int
            get() = actual.size

        /** 落在本模块各分组清单内、且系统里确实已停用的包 */
        val inScopeFrozen: List<String>
            get() {
                val scope = LinkedHashSet<String>()
                GROUPS.forEach { g -> scope.addAll(g.pkgs) }
                return scope.filter { actual.containsKey(it) }
            }

        /**
         * 本模块作用范围内、真正处于冻结态的条目数。
         *
         * 这是界面上最该给用户看的那个数：它既不像 [effectiveCount] 那样
         * 依赖 freeze.state 记录（记录丢了就归零），也不像 [systemDisabledCount]
         * 那样把用户的私人操作算进来。
         *
         * 非本模块清单内的停用包（用户自己禁的）不计入。
         */
        val inScopeCount: Int
            get() = inScopeFrozen.size

        fun isGroupOn(key: String): Boolean = groups[key] == true
    }'''

assert old in s, "Snapshot 段未匹配"
s = s.replace(old, new)

io.open(path, "w", encoding="utf-8").write(s)
print("FreezeData.kt 已更新，行数：", len(s.split(chr(10))))
