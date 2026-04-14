# 檔案鎖定規則（YukiBoard / Android 版）

日期: 2026-04-13  
適用專案: `SnowRealmYukiBoard`

## 1. 原則

- 預設最小修改範圍
- 未列入 task / handoff / file scope 的檔案，原則上不要碰
- 若發現需要跨檔修改，先說明理由
- 若同一檔案已有其他 agent 正在改，後進者先避開

## 2. 高敏感檔案

以下檔案與區域視為高敏感：

### 核心控制器

- `/app/src/main/java/com/snowrealm/yukiboard/service/YukiBoardIMEService.kt`
- `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/SettingsActivity.kt`

解鎖條件：

- 必須有明確 task / handoff
- 必須說明修改原因
- 優先做薄接線、切割、搬遷，不做無邊界擴張

### 建置與敏感設定

- `/.env`
- `/app/build.gradle`
- `/build.gradle`
- `/settings.gradle`
- `/gradle.properties`
- `/keystore/*`

解鎖條件：

- 只有在建置、環境、簽章、依賴確實需要時才動
- 修改前要明說目的

### 協作基線文件

- `/CODEX.md`
- `/docs/PROJECT_HEALTH_CHECK_2026-04-13.md`
- `/docs/ARCHITECTURE_REFACTOR_PLAN.md`
- `/docs/CLAUDE_CODEX_COLLAB_GUIDE.md`

解鎖條件：

- 當架構決策真的改變時才更新
- 不要每輪都隨手改

## 3. 條件鎖定區域

### UI Section / Settings 拆分區

- `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/section/*`
- `/app/src/main/java/com/snowrealm/yukiboard/ui/settings/*`

規則：

- 若 Claude Code 正在拆 section，Codex 優先避免碰同一批檔案
- 若必須修改，先讀目前 diff 與現況

### 新骨架區

- `/app/src/main/java/com/snowrealm/yukiboard/app/*`

規則：

- 這裡是新骨架落點
- 新的 `UiState`、`Coordinator`、`Repository` 優先加在這裡
- 不要另外開平行骨架到其他奇怪 package

## 4. 允許情況

以下情況可直接修改：

1. Task / handoff 已明確列出的檔案
2. 同一功能骨架所需的新檔案
3. 薄接線所需的最小既有檔案修改

## 5. 禁止情況

1. 為了省事，把新功能直接塞回超大核心檔
2. 因連鎖修改而順手碰一堆未授權檔案
3. 未確認現況就覆蓋其他 agent 正在進行的工作
4. 在無明確理由下修改 build / env / keystore 類檔案

## 6. 發現需要擴大範圍時

執行者應回報：

```text
NEED_CLARIFICATION

Reason: 需要修改 <檔案>
Detail: <原因>
Impact: <若不改，會卡住什麼>
```

## 7. 衝突處理

### 情境 1：同一檔案已有明顯並行修改

處理：

- 先讀 `git status` / `git diff`
- 能避開就避開
- 若不能避開，先交接再改

### 情境 2：需要修改高敏感檔案

處理：

- 可以改，但必須最小化
- 必須能解釋這次改動如何讓核心更瘦，而不是更胖

## 8. 違規判準

以下行為視為違規方向：

- 未說明就大改核心檔
- 直接覆蓋其他 agent 的拆分工作
- 默默擴大 file scope
- 讓結構債更深卻沒有交代
