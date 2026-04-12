模型可以有正常顏色嗎
看一下現有的光照設定：問題找到了。

**`IndirectLight.Builder().intensity(30000f).build(eng)` 沒有附加 cubemap（環境貼圖）。**

Filament 的 `IndirectLight` 沒有 cubemap 時，模型的 PBR 材質（metallic/roughness）計算出來是黑色或灰色，因為它不知道「環境」長什麼樣子，diffuse 和 specular 都算不出來。方向光補不回來，因為 VRM 模型用的是 PBR，不是 Phong。

解法有兩種：

**方案 A（簡單）：** 用一個純色的 cubemap 近似環境光，Filament 有內建 API 可以做這件事，不需要外部資源檔。

**方案 B（正確）：** 打包一個輕量 `.ktx` 環境貼圖，約 100-300KB，颜色才會真正準確。

現在先用方案 A 讓顏色出來，你可以之後再換方案 B 讓品質更好：清楚了，現在直接寫修法：

---

**問題很明確**

`IndirectLight.Builder().intensity(30000f).build(eng)` 沒有 cubemap，Filament 的 PBR 材質需要環境貼圖才能算出正確顏色。沒有的話 diffuse 和 specular 反射都算成 0，模型顏色跑掉（偏黑、偏灰、不飽和）。

Filament 1.51 的 `filament-utils` 有 `IBLPrefilter` API 可以從程序化生成 IBL，不需要外部 `.ktx` 檔。

**修改 `animation/VrmCharacterView.kt`**

找到 `initFilament()` 裡的這段：

```kotlin
// 原本（沒有 cubemap，顏色跑掉）：
val ibl = IndirectLight.Builder()
    .intensity(30000f)
    .build(eng)
scene?.indirectLight = ibl
```

替換成：

```kotlin
// 用 IBLPrefilter 生成白色環境光 IBL（正確顏色）
val iblPrefilter = IBLPrefilter(eng)

// 建立一個純白色的 equirectangular texture 做為 IBL 來源
// 1x1 白色像素代表均勻環境光
val white = floatArrayOf(1f, 1f, 1f, 1f)
val srcTexture = Texture.Builder()
    .width(1).height(1)
    .levels(1)
    .sampler(Texture.Sampler.SAMPLER_2D)
    .format(Texture.InternalFormat.RGBA16F)
    .build(eng)

val buffer = java.nio.FloatBuffer.wrap(white)
srcTexture.setImage(
    eng, 0,
    Texture.PixelBufferDescriptor(
        buffer,
        Texture.Format.RGBA,
        Texture.Type.FLOAT
    )
)

// 從白色 equirect 生成 reflections cubemap
val reflections = iblPrefilter.roughnessFilter(srcTexture)

val ibl = IndirectLight.Builder()
    .reflections(reflections)
    .intensity(50000f)   // 提高到 50000，顏色更飽和
    .build(eng)
scene?.indirectLight = ibl

// 清理暫時資源
iblPrefilter.destroy()
eng.destroyTexture(srcTexture)
```

同時把方向光強度調整，讓正面光照更均勻：

```kotlin
// 主方向光（正面偏上方）
val lightEntity = EntityManager.get().create()
LightManager.Builder(LightManager.Type.DIRECTIONAL)
    .color(1f, 1f, 1f)         // 純白光，不偏色
    .intensity(80000f)          // 稍微降低，避免過曝
    .direction(0f, -1f, -0.5f) // 正上方斜打
    .castShadows(false)
    .build(eng, lightEntity)
scene?.addEntity(lightEntity)

// 補充側面光（讓模型兩側不要全黑）
val fillLightEntity = EntityManager.get().create()
LightManager.Builder(LightManager.Type.DIRECTIONAL)
    .color(0.6f, 0.7f, 1.0f)   // 偏藍的冷光
    .intensity(30000f)
    .direction(-0.5f, -0.3f, 0.5f)  // 左後側
    .castShadows(false)
    .build(eng, fillLightEntity)
scene?.addEntity(fillLightEntity)
```

**如果 `IBLPrefilter` API 不存在（版本問題），用這個備用方案：**

```kotlin
// 備用方案：用多個點光源模擬均勻環境光
// （不需要 IBLPrefilter，任何版本都能用）

// 不設 indirectLight（或設很低強度）
val ibl = IndirectLight.Builder()
    .intensity(10000f)
    .build(eng)
scene?.indirectLight = ibl

// 四方向打光模擬環境
val lights = listOf(
    Triple(floatArrayOf(1f, 1f, 1f), 80000f, floatArrayOf(0f, -1f, -0.3f)),   // 正面上
    Triple(floatArrayOf(0.8f, 0.8f, 1f), 30000f, floatArrayOf(-1f, 0f, 0.5f)), // 左側
    Triple(floatArrayOf(1f, 0.9f, 0.8f), 20000f, floatArrayOf(1f, 0f, 0.5f)),  // 右側
    Triple(floatArrayOf(0.5f, 0.5f, 0.7f), 15000f, floatArrayOf(0f, 1f, 0.5f)), // 下方補光
)

lights.forEach { (color, intensity, direction) ->
    val entity = EntityManager.get().create()
    LightManager.Builder(LightManager.Type.DIRECTIONAL)
        .color(color[0], color[1], color[2])
        .intensity(intensity)
        .direction(direction[0], direction[1], direction[2])
        .castShadows(false)
        .build(eng, entity)
    scene?.addEntity(entity)
}
```

**這個備用方案不需要任何外部 API，直接能用，效果夠好。**

---

長期的正確做法是打包一個輕量 `.ktx` 環境貼圖，但那需要用 Filament 的 `cmgen` 工具從 HDR 生成，大概 100-300KB，這樣顏色才會完全正確（有反射、有漸變）。等基本顏色正常後再升級就好。