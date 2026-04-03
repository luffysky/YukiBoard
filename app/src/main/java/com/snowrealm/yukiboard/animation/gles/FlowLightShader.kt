package com.snowrealm.yukiboard.animation.gles

/**
 * 流光特效 GLSL Shader 原始碼。
 *
 * 效果：水滴落下產生漣漪，圓環緩慢向外擴散後消失，多滴交錯。
 * 時間由 uniform `uTime`（秒）驅動，每幀由 [FlowLightRenderer] 更新。
 */
object FlowLightShader {

    val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    /**
     * 片段 Shader：
     * - `uTime`  ：流逝秒數（float）
     * - `uAlpha` ：整體透明度（0.0 ~ 1.0）
     * - 水滴漣漪：每滴有生命週期，圓環擴散後淡出
     */
    val FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform float uTime;
        uniform float uAlpha;
        uniform float uAspect;
        uniform vec3  uColor;

        // 偽隨機 hash
        float hash(float n) {
            return fract(sin(n) * 43758.5453);
        }

        // 單滴漣漪：擴散中的圓環
        // dropTime = 這滴水滴已經過了多少秒（0 = 剛落下）
        // lifespan = 這滴水滴的完整生命週期（秒）
        float drop(vec2 uv, vec2 center, float dropTime, float lifespan) {
            if (dropTime < 0.0 || dropTime > lifespan) return 0.0;

            float progress = dropTime / lifespan;  // 0→1 生命進度

            // 圓環半徑隨時間擴大
            float radius = progress * 0.8;
            float dist = length(uv - center);

            // 圓環形狀：越靠近 radius 越亮，寬度隨時間變寬變淡
            float ringWidth = 0.02 + progress * 0.04;
            float ring = smoothstep(ringWidth, 0.0, abs(dist - radius));

            // 第二圈較淡的外環
            float radius2 = radius * 0.6;
            float ring2 = smoothstep(ringWidth * 0.8, 0.0, abs(dist - radius2)) * 0.4;

            // 生命衰減：後期淡出
            float fade = 1.0 - smoothstep(0.3, 1.0, progress);

            return (ring + ring2) * fade;
        }

        void main() {
            vec2 uv = vTexCoord;
            uv.x *= uAspect;  // 用實際寬高比校正，確保圓形

            float total = 0.0;

            // 3 滴水，各自有不同的週期和落點
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float period = 6.0 + hash(fi * 7.0) * 4.0;  // 每滴週期 6~10 秒
                float offset = hash(fi * 13.0) * period;     // 時間錯開

                // 目前這滴在生命週期中的位置
                float dropTime = mod(uTime + offset, period);

                // 每個週期落在不同位置（用週期編號當 seed）
                float cycle = floor((uTime + offset) / period);
                float cx = hash(fi * 3.0 + cycle * 17.0) * (uAspect - 0.3) + 0.15;
                float cy = hash(fi * 5.0 + cycle * 23.0) * 0.7 + 0.15;
                vec2 center = vec2(cx, cy);

                total += drop(uv, center, dropTime, period * 0.7);
            }

            float intensity = clamp(total * 0.25, 0.0, 1.0);

            // 主題色混合白色
            vec3 color = mix(uColor, vec3(1.0), intensity);

            gl_FragColor = vec4(color, intensity * uAlpha);
        }
    """.trimIndent()
}
