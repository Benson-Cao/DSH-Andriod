#!/usr/bin/env bash
# patch.sh —— 内置补丁，打进 APK assets，首启由 DshBootstrap 调用
#
# dsh 0.2.x 说明：v0.1 时代的兼容性补丁（node-pty 存在性检查、对
#   @deepseek-ai/node-addon-system/lib/flock.js 做 sed 替换）已全部不再需要：
#     · flock 兼容性已在 bundle 内的 flock.js 用 try/catch 降级成单实例 stub 解决，
#       不再用 sed 暴力改写，避免破坏已改造的加载逻辑；
#     · 0.2.x 不再依赖 node-pty 预编译二进制。
# 这里仅保留一个无副作用的占位脚本，保证 DshBootstrap 的首启调用路径不变
# （脚本退出码非 0 才会被 bootstrap 记 warning，故此处显式 exit 0）。
set -e

echo "==> dsh 0.2.x：运行环境补丁已全部内置，无需额外 patch"
exit 0
