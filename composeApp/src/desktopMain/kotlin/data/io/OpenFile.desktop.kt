package data.io

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.AwtWindow
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import data.Cluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import ui.EditClusterViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

// works on Linux, todo: test on Windows
@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    onOpen: (content: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    IconButton(onClick = {
        val sample = """
param1: abc
content:
- type: Cluster
  indices: [0, 4]
  circles:
  - x: 200.0
    y: 200.0
    r: 100.0
  - x: 250.0
    y: 200.0
    r: 100.0
  - x: 200.0
    y: 250.0
    r: 100.0
  - x: 250.0
    y: 250.0
    r: 100.0
  parts:
  - insides: [0]
    outsides: [1,2,3]
    fillColor: "#00ffff"
  filled: true
"""
        val cluster = EditClusterViewModel.UiState.DEFAULT.let { Cluster(it.circles, it.parts) }
        val ddc = Ddc(cluster)
        val module = SerializersModule {
            polymorphic(Ddc.Token::class) {
                subclass(Ddc.Token.Cluster::class)
                subclass(Ddc.Token.Circle::class)
            }
        }
        val config = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Property
        )
//        println(
//            Yaml(module, config).encodeToString(Ddc.serializer(), ddc)
//        )
//        val muh = parseDdc(sample)
//        println(muh)
        fileDialogIsOpen = true
    }) {
        Icon(iconPainter, contentDescription)
    }
    if (fileDialogIsOpen) {
        LoadFileDialog { directory, filename ->
            fileDialogIsOpen = false
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = filename?.let {
                        val file =
                            if (directory == null)
                                File(filename)
                            else File(directory, filename)
                        file.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    }
                    onOpen(content)
                } catch (e: IOException) {
                    onOpen(null)
                }
            }
        }
    }
}

@Composable
private fun LoadFileDialog(
    parent: Frame? = null,
    onCloseRequest: (directory: String?, filename: String?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a file", LOAD) {
            init {
                setFilenameFilter { dir, name ->
                    name.endsWith(".ddc") || name.endsWith(".yml") || name.endsWith(".yaml")
                }
            }
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(directory, file)
                }
            }
        }
    },
    dispose = FileDialog::dispose
)