# -*- coding: utf-8 -*-
"""纠正伪造机型的说明定位：目标是让抖音这类 App 认为你在用老机型，
从而触发它自己的「老机型省电/低画质」策略，降低解码与渲染开销。"""
import os, sys

BASE = "/storage/emulated/0/Z-ai工作区/_native/app/src/main/java/com/zimky/coregateUS"

def patch(rel, old, new):
    p = os.path.join(BASE, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s:
        print("MISS:", rel, "|", old[:50])
        sys.exit(1)
    s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print("OK:", rel)

# ── 1. SpoofData 文件头：把「让游戏以为是低端机」改成真实目的 ──
patch("core/SpoofData.kt",
      " *   3. 常见老款低端机（OPPO A5，用于「反向伪装」，让游戏以为是低端机）\n",
      " *   3. 常见老款低端机（OPPO A5，反向伪装：让抖音这类 App 认为你在用老机型，\n"
      " *      从而触发它自己的「老机型省电模式」—— 自动降分辨率、降码率、少做特效解码，\n"
      " *      从源头减少 CPU/GPU 负载，这也是本模块省电链路里最直接的一环）\n")

# ── 2. OPPO A5 档位副标题 ──
patch("core/SpoofData.kt",
      '        subtitle = "常见老款低端机 · 反向伪装，让游戏当成低端机",\n',
      '        subtitle = "老机型 · 让抖音等 App 触发老机型省电优化，降负载降功耗",\n')

# ── 3. SpoofPage：在「怎么让它生效」卡之前插一张「用来干什么」说明卡 ──
patch("page/SpoofPage.kt",
      "        // ── 卡 6：生效步骤 ────────────────────────────────────────\n",
      "        // ── 卡 5.5：这一页到底用来干什么 ─────────────────────────\n"
      "        //\n"
      "        // 这是本模块里唯一一个「目的是省电」的功能，必须写清楚，\n"
      "        // 否则用户会以为它是个解锁画质的玩具，选错档位反而更费电。\n"
      "        Card(modifier = Modifier.fillMaxWidth()) {\n"
      "            Column(Modifier.padding(14.dp)) {\n"
      "                LabelText(\"用来干什么\")\n"
      "                Gap(8.dp)\n"
      "                HintText(\n"
      "                    \"把本机伪装成老机型，是为了让抖音这类 App 自己切到「老机型省电模式」：\" +\n"
      "                        \"它们检测到机型老旧后，会主动降分辨率、降码率、少做特效和预解码，\" +\n"
      "                        \"CPU 与 GPU 的负载就下来了，发热和耗电随之下降。\"\n"
      "                )\n"
      "                Gap(8.dp)\n"
      "                HintText(\n"
      "                    \"所以「OPPO A5」这一档是省电用的，不是画质用的；\" +\n"
      "                        \"「iPhone 17 Pro Max」那一档反过来，是让 App 放开高清画质，会更耗电。\"\n"
      "                )\n"
      "                Gap(8.dp)\n"
      "                HintText(\"只想省电就选老机型档，并只给抖音这类耗电大户勾作用域。\")\n"
      "            }\n"
      "        }\n"
      "        Spacer(Modifier.height(12.dp))\n"
      "        // ── 卡 6：生效步骤 ────────────────────────────────────────\n")

print("done")