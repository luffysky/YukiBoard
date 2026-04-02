package com.snowrealm.yukiboard.animation.gles

/**
 * 流光特效 GLSL Shader 原始碼。
 *
 * 效果：多道彩色光波以正弦函式沿 X 軸掃過鍵盤，帶透明漸變。
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
     * - 三道光波疊加，顏色採 HSV 轉 RGB 近似
     */
    val FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform float uTime;
        uniform float uAlpha;

        // 簡易 HSV -> RGB（h: 0~1, s: 0~1, v: 0~1）
        vec3 hsv2rgb(float h, float s, float v) {
            float c = v * s;
            float x = c * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
            float m = v - c;
            vec3 rgb;
            if      (h < 1.0/6.0) rgb = vec3(c, x, 0.0);
            else if (h < 2.0/6.0) rgb = vec3(x, c, 0.0);
            else if (h < 3.0/6.0) rgb = vec3(0.0, c, x);
            else if (h < 4.0/6.0) rgb = vec3(0.0, x, c);
            else if (h < 5.0/6.0) rgb = vec3(x, 0.0, c);
            else                  rgb = vec3(c, 0.0, x);
            return rgb + m;
        }

        void main() {
            float x = vTexCoord.x;
            float y = vTexCoord.y;

            // 光波 1：主流光（快速，藍紫色調）
            float wave1 = sin((x - uTime * 0.6) * 8.0) * 0.5 + 0.5;
            float mask1 = smoothstep(0.0, 0.25, wave1) * smoothstep(1.0, 0.75, wave1);
            vec3  col1  = hsv2rgb(mod(uTime * 0.12 + x * 0.3, 1.0), 0.8, 1.0);

            // 光波 2：慢速、較寬（青色調）
            float wave2 = sin((x - uTime * 0.3) * 4.0 + 1.2) * 0.5 + 0.5;
            float mask2 = smoothstep(0.0, 0.35, wave2) * smoothstep(1.0, 0.65, wave2) * 0.6;
            vec3  col2  = hsv2rgb(mod(uTime * 0.08 + x * 0.2 + 0.5, 1.0), 0.7, 0.9);

            // 光波 3：亮點閃爍
            float sparkle = pow(sin((x - uTime * 1.0) * 14.0 + y * 6.0) * 0.5 + 0.5, 8.0) * 0.4;
            vec3  col3    = vec3(1.0, 1.0, 1.0);

            // 疊加
            vec3 finalColor = col1 * mask1 + col2 * mask2 + col3 * sparkle;
            float finalAlpha = (mask1 + mask2 * 0.6 + sparkle) * uAlpha;

            gl_FragColor = vec4(finalColor, clamp(finalAlpha, 0.0, 1.0));
        }
    """.trimIndent()
}
