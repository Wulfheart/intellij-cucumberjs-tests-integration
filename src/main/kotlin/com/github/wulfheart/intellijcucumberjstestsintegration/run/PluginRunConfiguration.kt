package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.nodejs.debug.NodeDebugRunConfiguration
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptBundle
import org.jetbrains.plugins.cucumber.javascript.run.ui.CucumberJavaScriptConfigurationEditorForm

data class ToRunItem(val filePath: String, val line: Int?)

class PluginRunConfiguration(project: Project, factory: ConfigurationFactory, name: String?) :
    LocatableConfigurationBase<Element>(project, factory, name), NodeDebugRunConfiguration {
    var toRun = mutableListOf<ToRunItem>()
    var cucumberJsArguments: String = ""
    var workingDirectory: String? = null
        get() {
            if (field != null) return field else return this.project.basePath
        }
    var myInterpreterRef: NodeJsInterpreterRef = NodeJsInterpreterRef.createProjectRef()
    var myEnvData: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT


    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super.readExternal(element)
        DefaultJDOMExternalizer.readExternal(this, element)
        this.myEnvData = EnvironmentVariablesData.readExternal(element)
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        DefaultJDOMExternalizer.writeExternal(this, element)
        if (EnvironmentVariablesData.DEFAULT != this.myEnvData) {
            this.myEnvData.writeExternal(element)
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return CucumberJavaScriptConfigurationEditorForm(this.getProject())
    }

    @Throws(ExecutionException::class)
    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val project = env.getProject()
        this.toRun.forEach { toRunItem ->
            val path = toRunItem.filePath
            if (VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(path)) != null) {
                val virtualFile: VirtualFile =
                    checkNotNull(VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(path)))

                val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
                if (module == null) {
                    throw ExecutionException(
                        CucumberJavaScriptBundle.message(
                            "dialog.message.can.t.find.module.for.file",
                            *arrayOfNulls<Any>(0)
                        )
                    )
                } else {
                    return PluginRunningState(env, this)
                }
            } else {
                throw ExecutionException(
                    CucumberJavaScriptBundle.message(
                        "dialog.message.can.t.find.file",
                        *arrayOf<Any?>(path)
                    )
                )
            }
        }

        throw ExecutionException("We did not anticipate this")

    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        this.toRun.forEach { toRun ->
            if (VirtualFileManager.getInstance()
                    .findFileByUrl(VfsUtilCore.pathToUrl(toRun.filePath)) == null
            ) {
                throw RuntimeConfigurationException(
                    CucumberJavaScriptBundle.message(
                        "dialog.message.can.t.find.file",
                        *arrayOf<Any?>(toRun.filePath)
                    )
                )
            }
        }

    }

    fun addFilePath(filePath: String) {
        val path = PathUtil.toSystemIndependentName(filePath)
        val item = ToRunItem(path, null)
        add(item)
    }
    fun addFilePathAndLine(filePath: String, line: Int) {
        val path = PathUtil.toSystemIndependentName(filePath)
        val item = ToRunItem(path, line)
        add(item)
    }

    private fun add(toRunItem: ToRunItem) {
        if(!this.toRun.contains(toRunItem)) {
            this.toRun.add(toRunItem)
        }
    }

}