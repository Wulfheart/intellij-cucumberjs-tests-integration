package com.github.wulfheart.intellijcucumberjstestsintegration.utils

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.File

fun getPluginFile(relativePath: String): File {
    val pluginId = PluginId.getId("com.github.wulfheart.intellijcucumberjstestsintegration") // Your plugin ID from plugin.xml
    val pluginPath = PluginManagerCore.getPlugin(pluginId)?.pluginPath
    return pluginPath?.resolve(relativePath)?.toFile()
        ?: throw IllegalStateException("Plugin path not found")
}

fun getPluginPath() {
    val pluginId = PluginId.getId("com.github.wulfheart.intellijcucumberjstestsintegration") // Your plugin ID from plugin.xml
    val pluginPath = PluginManagerCore.getPlugin(pluginId)?.pluginPath
}

object FormatterExtractor {

    @get:Throws(Exception::class)
    val formatterPath: File
        get() {


            // Target file next to the JAR
            val formatterFile: File = getPluginFile("lib/formatter.js")


            // Extract if needed
            if (!formatterFile.exists()) {
                // LOG.info("Extracting formatter.js to " + formatterFile.getAbsolutePath())

                FormatterExtractor::class.java.getResourceAsStream("/formatter/formatter.js").use { input ->
                    checkNotNull(input) { "formatter.js not found in resources" }
                    formatterFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    // Files.copy(input, formatterFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }

            return formatterFile
        }
}
