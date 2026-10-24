# -*- coding: utf-8 -*-
r"""
第 7 条脚本侧修复（v3）：--freeze-status 的 [applied] 段加反推兜底。

v2 失败原因：OLD/NEW 里 printf 的 \t \n 被多写了一层反斜杠，
与文件实际内容（单反斜杠）不匹配，assert 直接挂。

v3 改为「按行号切片」定位：不再手抄原文，
用 L1647-1654 的真实行拼出 OLD，彻底规避转义误差。

兜底只做一件事：组开关为 on、且组内包在系统里确实被停用/挂起时，
把它们以 包名<TAB>disable 补进 [applied]，让 effectiveCount 算出真值。
不凭空造数据，不影响冻结行为本身。
"""
import io

P = '/storage/emulated/0/Z-ai工作区/src/CoreGate-main/service.sh'
s = io.open(P, encoding='utf-8').read()
lines = s.split('\n')

# 定位 [applied] 段：从 echo "[applied]" 到对应的 fi
start = None
for i, ln in enumerate(lines):
    if ln.strip() == 'echo "[applied]"':
        start = i
        break
assert start is not None, '找不到 echo "[applied]"'

end = None
for j in range(start + 1, min(start + 20, len(lines))):
    if lines[j].strip() == 'fi':
        end = j
        break
assert end is not None, '找不到 [applied] 段的 fi'

old_lines = lines[start:end + 1]
print('[定位] [applied] 段位于 L%d ~ L%d' % (start + 1, end + 1))

IND = ' ' * 8
new_lines = old_lines[:]
# 把 if 条件改成「文件存在且非空」，并在 fi 前插入 else 兜底分支
new_lines[1] = new_lines[1].replace(
    'if [ -f "$FREEZE_STATE" ]; then',
    'if [ -f "$FREEZE_STATE" ] && [ -s "$FREEZE_STATE" ]; then'
)
assert '-s "$FREEZE_STATE"' in new_lines[1], 'if 条件改写失败'

fallback = [
    IND + 'else',
    IND + '    # 记录文件不存在或为空（freeze_restore_all 结尾会 rm 掉它，模块升级也可能丢）：',
    IND + '    # 用「组开关为 on 且组内包在系统里确实被停用/挂起」反推，',
    IND + '    # 免得界面把已经冻上的包报成「没有冻结」。只补真实出现在 actual 里的包。',
    IND + '    _fb_actual=$(freeze_status_report 2>/dev/null | cut -f1)',
    IND + '    echo "$FREEZE_TABLE" | while IFS= read -r _fb_line; do',
    IND + '        [ -z "$_fb_line" ] && continue',
    IND + '        _fb_key=${_fb_line%%|*}',
    IND + '        freeze_group_on "$_fb_key" || continue',
    IND + '        for _fb_p in $(freeze_group_pkgs "$_fb_key"); do',
    IND + '            [ -z "$_fb_p" ] && continue',
    IND + '            echo "$_fb_actual" | grep -qxF "$_fb_p" || continue',
    IND + '            printf "' + '%s' + '\\' + 't' + '%s' + '\\' + 'n' + '" "$_fb_p" "disable"',
    IND + '        done',
    IND + '    done',
]
# 在 fi（最后一行）之前插入 else 分支
new_lines = new_lines[:-1] + fallback + [new_lines[-1]]

out_lines = lines[:start] + new_lines + lines[end + 1:]
io.open(P, 'w', encoding='utf-8').write('\n'.join(out_lines))
print('[写入] 新增 %d 行' % (len(new_lines) - len(old_lines)))

back = io.open(P, encoding='utf-8').read()
assert '_fb_actual' in back
assert 'freeze_group_pkgs "$_fb_key"' in back
assert '-s "$FREEZE_STATE"' in back
print('[回读] 校验通过')
