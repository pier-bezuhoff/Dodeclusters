package data.io

import java.io.File

actual suspend fun saveTextFile(content: String, filename: String) {
    val originalFile = File(filename)
    val name = originalFile.nameWithoutExtension
    val extension = if (originalFile.extension.isNotBlank()) "." + originalFile.extension else ""
    var suffix: Int? = null
    fun newFilename(): String =
        name + (suffix ?: "") + extension
    while (File(newFilename()).exists()) {
        if (suffix == null)
            suffix = 1
        else
            suffix += 1
    }
    val file = File(newFilename())
    file.createNewFile()
    file.bufferedWriter().use { out ->
        content.lines().forEach { line ->
            out.write(line)
            out.newLine()
        }
    }
}