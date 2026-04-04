#!/usr/bin/env bash
# YukiBoard — 打包 + 上傳 Firebase App Distribution
set -e

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Android/Android Studio/jbr}"

NOTES="${1:-YukiBoard 測試版}"

echo "=== 打包 + 上傳 Firebase App Distribution ==="
echo "Release notes: $NOTES"
echo ""

./gradlew assembleDebug appDistributionUploadDebug \
  --releaseNotes="$NOTES" \
  --groups="testers"

echo ""
echo "=== 上傳完成 ==="
