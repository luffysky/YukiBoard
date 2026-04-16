package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.ui.panel.PanelType

/**
 * Sprint 5 Round 1：container / external state 的 read-only consume 邊界。
 *
 * 以同步 snapshot read 方式從 [ImeSessionCoordinator.uiState] 取出 container 相關欄位，
 * 為後續 reader 遷移（Round 2）提供集中入口。
 *
 * 責任：
 * - 提供 `isFloating / isPanelBehind / isInGame / remoteConnection / activePanel` 五個
 *   container-scope 狀態的同步快照讀取
 * - 讓未來的 reader 改以「單一 coordinator snapshot」為來源，取代現行散落的
 *   `YukiBoardIMEService` 內部可變欄位讀取
 *
 * 不負責：
 * - 寫入 container state（仍走 [ImeSessionCoordinator] 的 `setFloatingMode / setPanelBehindKeyboard /
 *   setGameMode / setRemoteConnectionState / showPanel` 等 setter；寫入側整併屬 Sprint 5 Round 3 範圍）
 * - 反應式 collect（屬未來 `renderContainerState` 層的職責）
 * - 熱路徑 RMW（AI / swipe / HANDWRITING 等屬 Sprint 4 保留 B 類，非本 Sprint 範圍）
 *
 * 設計：
 * - 與 `InputUiSync` 平行：一個負責 composing/candidates render 邊界，一個負責 container state consume
 *   邊界；兩者皆持有 coordinator 實例、走同步語意。
 * - 每個 getter 都從 `coordinator.uiState.value` 取 snapshot；不暴露 `StateFlow<ImeUiState>`，避免
 *   reader 直接觸及完整 UI state（破壞封裝）。
 */
class ImeContainerStateConsumer(
    private val coordinator: ImeSessionCoordinator,
) {
    val isFloating: Boolean
        get() = coordinator.uiState.value.isFloatingMode

    val isPanelBehind: Boolean
        get() = coordinator.uiState.value.isPanelBehindKeyboard

    val isInGame: Boolean
        get() = coordinator.uiState.value.isInGameMode

    val remoteConnection: ConnectionState
        get() = coordinator.uiState.value.remoteConnectionState

    val activePanel: PanelType?
        get() = coordinator.uiState.value.activePanel

    /**
     * 聚合 snapshot：一次讀取所有 container 欄位，避免呼叫端多次 get 產生不一致讀。
     * 未來 Round 2 的 reader 若需同時檢查多個欄位，建議用此 API 而非多次個別 getter。
     */
    fun snapshot(): ImeContainerSnapshot {
        val state = coordinator.uiState.value
        return ImeContainerSnapshot(
            isFloating = state.isFloatingMode,
            isPanelBehind = state.isPanelBehindKeyboard,
            isInGame = state.isInGameMode,
            remoteConnection = state.remoteConnectionState,
            activePanel = state.activePanel,
        )
    }
}

/**
 * 聚合 snapshot 型別。只含 container-scope 欄位，不含 composing/candidates/theme/voice 等其他
 * UI state，避免無意濫用。
 */
data class ImeContainerSnapshot(
    val isFloating: Boolean,
    val isPanelBehind: Boolean,
    val isInGame: Boolean,
    val remoteConnection: ConnectionState,
    val activePanel: PanelType?,
)
