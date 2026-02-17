package com.github.wulfheart.intellijcucumberjstestsintegration.startup

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.cucumber.javascript.run.CucumberJavaScriptRunConfigurationType
import org.jetbrains.plugins.cucumber.run.CucumberRunLineMarkerContributor
import kotlin.collections.forEach
import kotlin.jvm.java


class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")

        val epName = ExtensionPointName.create<LanguageExtensionPoint<*>>(
            "com.intellij.runLineMarkerContributor"
        )

         epName.extensionList.forEach { e ->
            if(e.implementationClass == CucumberRunLineMarkerContributor::class.java.name) {
                epName.point.unregisterExtension(e)

            }

        }

        val configurationTypes = ExtensionPointName.create<ConfigurationType>(
            "com.intellij.configurationType"
        )
        configurationTypes.extensionList.forEach { e ->
            if(e.javaClass.name == CucumberJavaScriptRunConfigurationType::class.java.name) {
                configurationTypes.point.unregisterExtension(e)

            }
        }

        val runConfigurationProducers = ExtensionPointName.create<RunConfigurationProducer<*>>(
            "com.intellij.runConfigurationProducer"
        )
        runConfigurationProducers.extensionList.forEach { e ->
            if(e.javaClass.name == "org.jetbrains.plugins.cucumber.javascript.run.CucumberJavaScriptRunConfigurationProducer") {
                runConfigurationProducers.point.unregisterExtension(e)

            }
        }
    }

}