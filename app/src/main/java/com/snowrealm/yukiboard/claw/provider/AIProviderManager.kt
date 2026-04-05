package com.snowrealm.yukiboard.claw.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.snowrealm.yukiboard.BuildConfig

class AIProviderManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        private const val PREFS_NAME = "yukiboard_ai_secure"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_BUILDCONFIG_IMPORTED = "buildconfig_keys_imported"

        @Volatile
        private var instance: AIProviderManager? = null

        fun getInstance(context: Context): AIProviderManager {
            return instance ?: synchronized(this) {
                instance ?: AIProviderManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private fun apiKeyPref(type: AIProviderType): String = "api_key_${type.name}"
        private fun selectedModelPref(type: AIProviderType): String = "selected_model_${type.name}"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val providerCache = mutableMapOf<AIProviderType, AIProvider>()

    init {
        importBuildConfigKeysIfNeeded()
    }

    /**
     * On first initialization, auto-import API keys from BuildConfig (loaded from .env).
     * Sets Anthropic as default if its key exists, otherwise OpenAI.
     */
    private fun importBuildConfigKeysIfNeeded() {
        if (prefs.getBoolean(KEY_BUILDCONFIG_IMPORTED, false)) return

        val anthropicKey = BuildConfig.ANTHROPIC_API_KEY
        val openaiKey = BuildConfig.OPENAI_API_KEY

        if (anthropicKey.isNotBlank()) {
            saveApiKey(AIProviderType.ANTHROPIC, anthropicKey)
        }
        if (openaiKey.isNotBlank()) {
            saveApiKey(AIProviderType.OPENAI, openaiKey)
        }

        // Set default provider: prefer Anthropic → OpenAI → SnowRealm (proxy)
        when {
            anthropicKey.isNotBlank() -> setSelectedProvider(AIProviderType.ANTHROPIC)
            openaiKey.isNotBlank() -> setSelectedProvider(AIProviderType.OPENAI)
            else -> setSelectedProvider(AIProviderType.SNOWREALM)
        }

        prefs.edit().putBoolean(KEY_BUILDCONFIG_IMPORTED, true).apply()
    }

    fun getProvider(type: AIProviderType): AIProvider {
        return providerCache.getOrPut(type) {
            val apiKey = getApiKey(type) ?: ""
            when (type) {
                AIProviderType.OPENAI -> OpenAIProvider(apiKey)
                AIProviderType.ANTHROPIC -> AnthropicProvider(apiKey)
                AIProviderType.GEMINI -> GeminiProvider(apiKey)
                AIProviderType.GROQ -> GroqProvider(apiKey)
                AIProviderType.SNOWREALM -> SnowRealmProvider(appContext)
            }
        }
    }

    fun getCurrentProvider(): AIProvider {
        return getProvider(getSelectedProviderType())
    }

    fun saveApiKey(type: AIProviderType, key: String) {
        prefs.edit().putString(apiKeyPref(type), key).apply()
        // Invalidate cached provider so it picks up the new key
        providerCache.remove(type)
    }

    fun getApiKey(type: AIProviderType): String? {
        return prefs.getString(apiKeyPref(type), null)
    }

    fun setSelectedProvider(type: AIProviderType) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, type.name).apply()
    }

    fun getSelectedProviderType(): AIProviderType {
        val name = prefs.getString(KEY_SELECTED_PROVIDER, AIProviderType.OPENAI.name)
        return try {
            AIProviderType.valueOf(name ?: AIProviderType.OPENAI.name)
        } catch (_: IllegalArgumentException) {
            AIProviderType.OPENAI
        }
    }

    fun setSelectedModel(type: AIProviderType, model: String) {
        prefs.edit().putString(selectedModelPref(type), model).apply()
    }

    fun getSelectedModel(type: AIProviderType): String {
        val provider = getProvider(type)
        return prefs.getString(selectedModelPref(type), provider.defaultModel)
            ?: provider.defaultModel
    }

    fun hasValidConfig(): Boolean {
        val type = getSelectedProviderType()
        if (type == AIProviderType.SNOWREALM) return true  // 用邀請碼，不需 API key
        val key = getApiKey(type)
        return !key.isNullOrBlank()
    }
}
