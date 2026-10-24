# -*- coding: utf-8 -*-
"""删除「开不开机 / 变砖」类误导表述，并纠正伪造机型的真实定位。"""
import os, sys

BASE = "/storage/emulated/0/Z-ai工作区/_native/app/src/main/java/com/zimky/coregateUS"

def patch(rel, old, new, must=True):
    p = os.path.join(BASE, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s:
        print("MISS:", rel, "|", old[:40])
        if must:
            sys.exit(1)
        return
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8').write(s)
    print("OK:", rel)

# 1) SpoofPage 文件头注释
patch("page/SpoofPage.kt",
      " * 旧版这一页特意只放说明和现状，理由是「改机型属性改错会开不了机」。\n",
      " * 旧版这一页特意只放说明和现状，理由是「直接写系统属性风险太高」。\n")

# 2) Xposed system_server 注释
patch("xposed/CoreGateXposed.kt",
      " * 改 system_server 的属性读数等于改全局，那正是「属性写错开不了机」的风险来源。\n",
      " * 改 system_server 的属性读数等于改全局，污染面太大，风险不可控。\n")

# 3) FreezePage 确认弹窗
patch("page/FreezePage.kt",
      '                    text = "此栏仅在 16.0.1 版本实验通过，其他版本可能会导致无法正常进系统。" +\n'
      '                        "请确保有救砖模块，并且能正常进 Recovery 后自行选择尝试。",',
      '                    text = "此栏仅在 16.0.1 版本实验通过。冻结作用在应用上，不涉及系统服务，" +\n'
      '                        "解除冻结只需回到本页关闭该分组，或直接重启设备。",')

print("---- 残留检查 ----")
import subprocess
r = subprocess.run(["grep", "-rn", "开不开机", "--include=*.kt", BASE],
                   capture_output=True, text=True)
print(r.stdout.strip() or "(无残留)")
r2 = subprocess.run(["grep", "-rn", "变砖", "--include=*.kt", BASE],
                    capture_output=True, text=True)
print(r2.stdout.strip() or "(无 变砖)")
