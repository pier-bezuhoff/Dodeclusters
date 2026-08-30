package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@Composable
actual fun rememberShader(
    shaderCode: String,
    vararg parameters: Shaders.UniformParameter,
    localMatrix: FloatArray?,
): Shader {
    val builder = remember(shaderCode) {
        // SkSL
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderCode))
    }
    val shader = remember(builder, parameters, localMatrix) {
        for (parameter in parameters) {
            when (parameter) {
                is Shaders.UniformParameter.Color ->
                    builder.uniform(parameter.name,
                        parameter.value.red, parameter.value.green, parameter.value.blue,
                        parameter.value.alpha,
                    )
                is Shaders.UniformParameter.Float ->
                    builder.uniform(parameter.name, parameter.value)
                is Shaders.UniformParameter.Float2 ->
                    builder.uniform(parameter.name, parameter.value1, parameter.value2)
            }
        }
        builder.makeShader(
            localMatrix =
                if (localMatrix == null)
                    null
                else
                    Matrix33(
                        localMatrix[0],
                        localMatrix[1],
                        localMatrix[2],
                        localMatrix[3],
                        localMatrix[4],
                        localMatrix[5],
                        localMatrix[6],
                        localMatrix[7],
                        localMatrix[8],
                    )
        )
    }
    return shader.asComposeShader()
}
