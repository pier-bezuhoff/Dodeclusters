package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush


// language=GLSL
private const val LINE_GLOW_SHADER = """
uniform shader background; // automatically supplied by compose
uniform float2 size;
uniform half4 glowColor;
// line params
uniform float a;
uniform float b;
uniform float c;

half4 main(float2 pixel) {
    float d = abs(a * pixel.x + b * pixel.y + c); // distance to the line
    float mask = int(d < 30);
    float intensity = mask * exp(-0.03 * (d - 20.0));
    half4 color = intensity * glowColor;
    return background.eval(pixel) + color;
}
"""

object Shaders {
    sealed interface UniformParameter {
        val name: String

        data class Color(
            override val name: String,
            val value: androidx.compose.ui.graphics.Color
        ) : UniformParameter
        data class Float(
            override val name: String,
            val value: kotlin.Float,
        ) : UniformParameter
        data class Float2(
            override val name: String,
            val value1: kotlin.Float,
            val value2: kotlin.Float,
        ) : UniformParameter
    }

    // see: https://medium.com/@popradi.arpad11/integrating-shaders-into-a-compose-multiplatform-project-1bb4e55aced1
    fun createGlowBrush(): Brush {
        val brush = ShaderBrush(
            TODO()
        )
        return brush
    }
}

/**
 * @param[localMatrix] post-applied 3x3 matrix [*row1, *row2, *row3]
 */
@Composable
expect fun rememberShader(
    shaderCode: String,
    vararg parameters: Shaders.UniformParameter,
    localMatrix: FloatArray? = null,
): Shader

