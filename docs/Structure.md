請幫我建立 YukiBoard Android 輸入法的完整專案架構。

專案定位：SnowRealm 生態系的動畫鍵盤 App，雪凜角色浮動 + 全鍵盤流光特效

架構：MVVM + Clean Architecture
動畫技術：OpenGL ES（流光特效）+ Lottie（角色動畫）

主要功能：
1. 英文 QWERTY 鍵盤
2. 注音輸入（libchewing JNI，架構預留）
3. 中英混合自動偵測（InputRouter，雙軌並行）
4. 全鍵盤流光動畫（OpenGL ES Shader）
5. 角色動畫區域（Lottie，雪凜浮動）
6. 語音輸入（Android SpeechRecognizer，介面預留替換）
7. 主題皮膚系統

請建立完整目錄結構和所有基礎檔案，包含：

app/src/main/
├── java/com/snowrealm/yukiboard/
│   ├── service/
│   │   └── YukiBoardIMEService.kt        # InputMethodService 主服務
│   ├── ui/
│   │   ├── keyboard/
│   │   │   ├── KeyboardView.kt           # 自訂鍵盤 View
│   │   │   ├── KeyboardViewModel.kt      # MVVM ViewModel
│   │   │   └── KeyboardState.kt          # UI 狀態
│   │   └── character/
│   │       └── CharacterView.kt          # 角色動畫區域
│   ├── input/
│   │   ├── router/
│   │   │   └── InputRouter.kt            # 中英混合偵測核心
│   │   ├── engine/
│   │   │   ├── InputEngine.kt            # 引擎介面
│   │   │   ├── EnglishInputEngine.kt     # 英文引擎
│   │   │   └── ChewingInputEngine.kt     # 注音引擎（預留 JNI）
│   │   └── candidate/
│   │       └── CandidateManager.kt       # 候選詞管理
│   ├── animation/
│   │   ├── CharacterAnimation.kt         # 角色動畫介面
│   │   ├── LottieCharacterAnimation.kt   # Lottie 實作
│   │   └── gles/
│   │       ├── FlowLightRenderer.kt      # OpenGL ES 流光 Renderer
│   │       ├── FlowLightShader.kt        # GLSL Shader
│   │       └── KeyboardGLSurfaceView.kt  # GLSurfaceView
│   ├── speech/
│   │   ├── SpeechInputEngine.kt          # 語音介面
│   │   └── AndroidSpeechEngine.kt        # 原生實作
│   ├── theme/
│   │   ├── ThemeManager.kt               # 皮膚管理
│   │   └── KeyboardTheme.kt              # 主題資料類別
│   └── language/
│       ├── LanguagePack.kt               # 語言包介面
│       └── EnglishLanguagePack.kt        # 英文語言包

請同時建立：
- AndroidManifest.xml（IME service 宣告、權限）
- build.gradle（Lottie、OpenGL ES、ViewModel、Coroutines 依賴）
- res/xml/method.xml（輸入法設定）
- res/layout/keyboard_layout.xml（鍵盤主佈局）
- CLAUDE.md（專案說明，給 Claude Code 讀的）

每個檔案都要有完整的基礎程式碼，不要只有空殼。
特別注意 InputRouter 的中英雙軌並行邏輯要寫完整。