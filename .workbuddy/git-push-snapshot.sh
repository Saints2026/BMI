#!/usr/bin/env bash
# ============================================================================
#  BMI 快照归档推送脚本（可直接在 Git Bash 执行）
#  命令顺序：切回 main → pull main → add → commit(快照标记) → push main → 打 tag
#
#  ⚠ 重要前提（已核实）：当前处于 feature-ai 分支，不是 main。
#    脚本先强制切回 main（丢弃 feature-ai 工作区污染，绝不合并该分支），
#    再把已还原的快照资产写回 main 工作区，然后按指定顺序提交推送。
#  ⚠ 密钥安全：ai-key.properties / db-config.properties 已被 .gitignore 忽略，
#    不会进入暂存；feature-ai 历史里的泄露密钥(cfe1efb)请另行轮换，勿合并该分支。
#  ⚠ 编译提醒：src/.../AiRequest.java 与 AiHealthResult.java 为 feature-ai 污染版
#    （与 AiService 引用不匹配，无法编译）。如需 main 可编译，提交前先按
#    docs/ai_design.md §3 重建这两个 DTO。
# ============================================================================

REPO="/d/Users/Desktop/BMI/BMI"
BK="/d/Users/Desktop/BMI/_snap_bk_20260714"   # 仓库外临时备份，可回退
cd "$REPO" || { echo "!!! 无法进入 $REPO"; exit 1; }

# -- 0) 切分支前备份快照资产（防止未跟踪文件在 switch 时丢失）--
rm -rf "$BK"; mkdir -p "$BK"
cp -r db src docs "$BK"/
cp CODEBUDDY.md "$BK"/ 2>/dev/null

# -- 1) 切回 main 并拉取远端 --
git switch -f main
git pull origin main

# -- 2) 还原快照资产到 main 工作区 --
cp -r "$BK"/db ./
cp -r "$BK"/src ./
cp -r "$BK"/docs ./
cp "$BK"/CODEBUDDY.md ./

# -- 3) 安全闸门：密钥不得进入暂存（命中立即中止，非交互）--
if git status --porcelain | grep -qiE "ai-key\.properties|db-config\.properties"; then
  echo "!!! 检测到密钥文件出现在待提交列表，已中断。请检查 .gitignore。"
  exit 1
fi

# -- 4) 暂存生成资产（按指定清单）--
git add db/ src/ docs/ CODEBUDDY.md

# -- 5) 提交（快照标记备注）--
git commit -m "SNAP-20260714-001 完整归档BMI项目全量源码与规范文档"

# -- 6) 推送 origin/main --
git push origin main

# -- 7) 打快照 tag 并推送 --
git tag -a SNAP-20260714-001 -m "SNAP-20260714-001 BMI项目全量源码与规范文档完整归档"
git push origin SNAP-20260714-001

echo "完成：origin/main 已更新，标签 SNAP-20260714-001 已推送。"
echo "备份位置（可回退）: $BK"
