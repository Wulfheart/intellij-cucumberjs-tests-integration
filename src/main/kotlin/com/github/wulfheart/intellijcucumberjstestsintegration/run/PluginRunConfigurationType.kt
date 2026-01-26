package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil.findConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.javascript.cucumber.icons.JavascriptCucumberIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullFactory
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptBundle
import javax.swing.Icon

class PluginRunConfigurationType : ConfigurationTypeBase(
    "cucumber.js.extended",
    CucumberJavaScriptBundle.message("notification.name.cucumber.js", *arrayOfNulls<Any>(0)),
    CucumberJavaScriptBundle.message("notification.description.cucumber.js.run.configuration", *arrayOfNulls<Any>(0)),
    NotNullLazyValue.createValue<Icon?>(NotNullFactory { JavascriptCucumberIcons.CucumberJS })
), DumbAware {
    init {
        this.addFactory(PluginConfigurationFactory(this))
    }

    override fun getHelpTopic(): String {
        return "reference.dialogs.rundebug.cucumber.js"
    }

    class PluginConfigurationFactory(type: PluginRunConfigurationType) :
        ConfigurationFactory(type) {
        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return PluginRunConfiguration(project, this, "Cucumber.js")
        }

        override fun getId(): String {
            return "Cucumber.js"
        }
    }
}

fun getInstance(): PluginRunConfigurationType {
    return findConfigurationType(PluginRunConfigurationType::class.java)
}