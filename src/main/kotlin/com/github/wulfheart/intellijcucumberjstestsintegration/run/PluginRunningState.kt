package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.nodejs.NodeCommandLineUtil
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.javascript.nodejs.debug.NodeLocalDebugRunProfileState
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.text.SemVer
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptBundle
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptDisposable
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptUtil
import org.jetbrains.plugins.cucumber.javascript.run.CucumberPackage
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import java.nio.charset.StandardCharsets

class PluginRunningState(
    private val myExecutionEnvironment: ExecutionEnvironment,
    private val myRunConfiguration: PluginRunConfiguration
) : RunProfileState, NodeLocalDebugRunProfileState {
    @Throws(ExecutionException::class)
    private fun getCommand(debugPort: Int): GeneralCommandLine {
        val commandLine = GeneralCommandLine()
        commandLine.setCharset(StandardCharsets.UTF_8)
        this.myRunConfiguration.myEnvData.configureCommandLine(commandLine, true)
        val interpreter =
            this.myRunConfiguration.myInterpreterRef.resolveAsLocal(this.myRunConfiguration.getProject())
        commandLine.setExePath(interpreter.interpreterSystemDependentPath)
        NodeCommandLineUtil.addNodeOptionsForDebugging(
            commandLine,
            mutableListOf<String?>(),
            debugPort,
            true,
            interpreter,
            true
        )
        val cucumberNodePackage =
            CucumberJavaScriptUtil.getCucumberPackage(this.myRunConfiguration.getProject(), interpreter)
        var cucumberExecutable = "/bin/cucumber"
        if (cucumberNodePackage.version != null && cucumberNodePackage.version!!.getMajor() >= 4) {
            cucumberExecutable = "/bin/cucumber-js"
        }

        val var10000 = cucumberNodePackage.systemDependentPath
        val cucumberExecutablePath = FileUtil.toSystemDependentName(var10000 + cucumberExecutable)
        commandLine.addParameter(cucumberExecutablePath)

        this.myRunConfiguration.toRun.forEach { runItem ->
            val fileToRun = virtualFileFromPath(runItem.filePath)
            if(fileToRun != null) {
                if(runItem.line !== null) {
                    commandLine.addParameter(fileToRun.path + ":" + runItem.line)
                } else {
                    commandLine.addParameter(fileToRun.path)
                }

            }
        }

        // Good enough for now, we assume that they are all in the same package.json
        val fileToRun = virtualFileFromPath(this.myRunConfiguration.toRun.first().filePath);
        if (fileToRun != null) {

            if (!this.myRunConfiguration.cucumberJsArguments.isEmpty()) {
                commandLine.addParameters(
                    *this.myRunConfiguration.cucumberJsArguments.split(" ".toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                )
            }

            val cucumberPackage = CucumberPackage.fromNodePackage(cucumberNodePackage)
            val workingDirectory = this.workingDir
            commandLine.withWorkDirectory(workingDirectory)
            if (cucumberPackage.version != null && cucumberPackage.version!!.isGreaterOrEqualThan(7, 0, 0)) {
                addCommandLineParameters(
                    commandLine,
                    workingDirectory,
                    cucumberPackage,
                    CucumberJavaScriptUtil.getV7FormatterPath()
                )
            } else {
                throw Exception("Cucumber version 3, 4, 5 and 6 are not supported anymore. Please upgrade to version 7 or higher.")
            }

            val packageJson = PackageJsonUtil.findUpPackageJson(fileToRun)
            // val isESM = packageJson != null && PackageJsonData.getOrCreate(packageJson).isModuleType
            // if (isESM && cucumberPackage.version!!.isGreaterOrEqualThan(
            //         8,
            //         0,
            //         0
            //     )
            // ) {
            //     commandLine.addParameter("--import")
            // } else {
            //     commandLine.addParameter("--require")
            // }
            //
            // commandLine.addParameter(fileToRun.getPath())
            return commandLine
        } else {
            throw ExecutionException(
                CucumberJavaScriptBundle.message(
                    "dialog.message.can.t.find.feature.to.run",
                    *arrayOfNulls<Any>(0)
                )
            )
        }
    }

    private val workingDir: String
        get() {
            val workingDir = this.myRunConfiguration.workingDirectory
            return FileUtil.toSystemIndependentName(workingDir ?: "")
        }

    private fun createSMTRunnerConsoleView(): ConsoleView {
        val testConsoleProperties: TestConsoleProperties =
            SMTRunnerConsoleProperties(this.myRunConfiguration, "cucumber", this.myExecutionEnvironment.getExecutor())
        val consoleView: ConsoleView = SMTestRunnerConnectionUtil.createConsole("cucumber", testConsoleProperties)
        consoleView.addMessageFilter(UrlFilter())
        Disposer.register(
            CucumberJavaScriptDisposable.getInstance(this.myExecutionEnvironment.getProject()),
            consoleView
        )
        return consoleView
    }

    @Throws(ExecutionException::class)
    override fun execute(debugPort: Int): ExecutionResult {
        val commandLine = this.getCommand(debugPort)
        val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
        val consoleView = this.createSMTRunnerConsoleView()
        ProcessTerminatedListener.attach(processHandler)
        consoleView.attachToProcess(processHandler)
        return DefaultExecutionResult(consoleView, processHandler)
    }

    companion object {
        @Throws(ExecutionException::class)
        private fun addCommandLineParameters(
            commandLine: GeneralCommandLine,
            workingDirectory: String,
            cucumberPackage: CucumberPackage,
            formatterPath: String
        ) {
            commandLine.addParameter("--format")
            if (cucumberPackage.version != null && cucumberPackage.version!!.getMajor() == 2) {
                val relativeFormatterPath = FileUtil.getRelativePath(workingDirectory, formatterPath, '/')
                if (relativeFormatterPath == null) {
                    throw ExecutionException(
                        CucumberJavaScriptBundle.message(
                            "run.configuration.state.cant.find.path.to.formatter",
                            *arrayOf<Any>(formatterPath, workingDirectory)
                        )
                    )
                }

                commandLine.addParameter(FileUtil.toSystemDependentName(relativeFormatterPath))
            } else {
                commandLine.addParameter(escapePathParameter(formatterPath, cucumberPackage.version))
            }

            commandLine.addParameter("--format-options")
            commandLine.addParameter(String.format("{\"cucumberLibPath\": \"%s\"}", cucumberPackage.libPath))
            if (cucumberPackage.version != null && cucumberPackage.version!!.isGreaterOrEqualThan(5, 0, 0)) {
                commandLine.addParameter("--format-options")
                commandLine.addParameter("{\"colorsEnabled\": true}")
            }

            if (cucumberPackage.version != null && cucumberPackage.version!!.isGreaterOrEqualThan(
                    7,
                    0,
                    0
                ) && !cucumberPackage.version!!.isGreaterOrEqualThan(9, 4, 0)
            ) {
                commandLine.addParameter("--publish-quiet")
            }
        }

        private fun escapePathParameter(formatterPath: String, cucumberVersion: SemVer?): String {
            val path = FileUtil.toSystemDependentName(formatterPath)
            return if (FileUtil.isAbsolute(path) && SystemInfoRt.isWindows && cucumberVersion != null && cucumberVersion.isGreaterOrEqualThan(
                    8,
                    0,
                    0
                )
            ) StringUtil.wrapWithDoubleQuote("file://" + path) else path
        }

        private fun virtualFileFromPath(path: String): VirtualFile? {
            return VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(path))
        }
    }
}
