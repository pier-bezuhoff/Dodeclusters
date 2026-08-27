package ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toColorLong
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import domain.collectWithLifecycle

@Composable
actual fun rememberShader(
    shaderCode: String,
    vararg parameters: Shaders.UniformParameter,
    localMatrix: FloatArray?,
): Shader {
    val shader = remember(shaderCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // technically uses AGSL
            // reference: https://developer.android.com/reference/android/graphics/RuntimeShader
            val shader = RuntimeShader(shaderCode)
            shader
        } else {
            // stub
            val bitmap = Bitmap.createBitmap(
                intArrayOf(Color.TRANSPARENT),
                1, 1,
                Bitmap.Config.ARGB_8888
            )
            BitmapShader(bitmap,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(shader, lifecycleOwner.lifecycle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader is RuntimeShader) {
            val parametersFlow = snapshotFlow { parameters }
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parametersFlow.collect { params ->
                    for (parameter in params) {
                        when (parameter) {
                            is Shaders.UniformParameter.Color ->
                                shader.setColorUniform(parameter.name, parameter.value.toColorLong())
                            is Shaders.UniformParameter.Float ->
                                shader.setFloatUniform(parameter.name, parameter.value)
                            is Shaders.UniformParameter.Float2 ->
                                shader.setFloatUniform(parameter.name, parameter.value1, parameter.value2)
                        }
                    }
                }
            }
        }
    }
    // will it work? remember might deny localMatrix changes
    val matrixFlow = remember { snapshotFlow { localMatrix } }
    matrixFlow.collectWithLifecycle(shader) { m ->
        shader.setLocalMatrix(
            if (m == null)
                null
            else
                Matrix().apply { setValues(m) }
        )
    }
    return shader
}
