package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.github.wulfheart.intellijcucumberjstestsintegration.actions.ExecuteScenarioAction
import com.github.wulfheart.intellijcucumberjstestsintegration.utils.FormatterExtractor
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.javascript.nodejs.NodeCommandLineUtil
import com.intellij.javascript.nodejs.debug.NodeLocalDebugRunProfileState
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.extensions.PluginId
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
import java.nio.charset.StandardCharsets


class TestLocator : SMTestLocator {
    companion object {
        const val PROTOCOL = "file"
    }

    override fun getLocation(
        protocol: String,
        path: String,
        project: com.intellij.openapi.project.Project,
        scope: com.intellij.psi.search.GlobalSearchScope
    ): MutableList<com.intellij.execution.Location<out com.intellij.psi.PsiElement>> {
        if (protocol != PROTOCOL) return mutableListOf()

        // Parse path:line format (e.g., "/path/to/file.feature:123")
        val (filePath, line) = parsePathAndLine(path)

        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return mutableListOf()

        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
            ?: return mutableListOf()

        // If we have a line number, try to find the element at that line
        if (line != null && line > 0) {
            val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile)
            if (document != null && line <= document.lineCount) {
                val offset = document.getLineStartOffset(line - 1) // line is 1-indexed
                val element = psiFile.findElementAt(offset)
                if (element != null) {
                    return mutableListOf(com.intellij.execution.PsiLocation.fromPsiElement(element))
                }
            }
        }

        // Fallback to the file itself
        return mutableListOf(com.intellij.execution.PsiLocation.fromPsiElement(psiFile))
    }

    private fun parsePathAndLine(path: String): Pair<String, Int?> {
        // Path format: "/path/to/file.feature:123" or just "/path/to/file.feature"
        val lastColonIndex = path.lastIndexOf(':')
        if (lastColonIndex > 0) {
            val potentialLine = path.substring(lastColonIndex + 1)
            val line = potentialLine.toIntOrNull()
            if (line != null) {
                return Pair(path.substring(0, lastColonIndex), line)
            }
        }
        return Pair(path, null)
    }
}

class CucumberConsoleProperties(
    config: PluginRunConfiguration,
    executor: com.intellij.execution.Executor
) : SMTRunnerConsoleProperties(config, "cucumber", executor) {
    val locator: SMTestLocator

    init {
        locator = TestLocator()
        // isIdBasedTestTree = true
    }

    override fun getTestLocator(): SMTestLocator? {
        return locator;
    }

    override fun createRerunFailedTestsAction(
        consoleView: ConsoleView
    ): AbstractRerunFailedTestsAction {
        return ExecuteScenarioAction(consoleView)
    }
}

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
            if (fileToRun != null) {
                if (runItem.line !== null) {
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
                // val path = FormatterExtractor.formatterPath.absolutePath
                val path = "/home/alex/Code/intellij-cucumberjs-tests-integration/src/main/resources/formatter/formatter.js"

                addCommandLineParameters(
                    commandLine,
                    workingDirectory,
                    cucumberPackage,
                    path
                )
            } else {
                throw Exception("Cucumber version 3, 4, 5 and 6 are not supported anymore. Please upgrade to version 7 or higher.")
            }

            val packageJson = PackageJsonUtil.findUpPackageJson(fileToRun)
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

    private fun createSMTRunnerConsoleView(): Pair<ConsoleView, CucumberConsoleProperties> {
        val testConsoleProperties = CucumberConsoleProperties(
            this.myRunConfiguration,
            this.myExecutionEnvironment.executor
        )

        val consoleView: ConsoleView = SMTestRunnerConnectionUtil.createConsole("cucumber", testConsoleProperties)
        consoleView.addMessageFilter(UrlFilter())
        Disposer.register(
            CucumberJavaScriptDisposable.getInstance(this.myExecutionEnvironment.getProject()),
            consoleView
        )
        return Pair(consoleView, testConsoleProperties)
    }

    @Throws(ExecutionException::class)
    override fun execute(debugPort: Int): ExecutionResult {
        val commandLine = this.getCommand(debugPort)
        val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
        val (consoleView, consoleProperties) = this.createSMTRunnerConsoleView()
        ProcessTerminatedListener.attach(processHandler)
        consoleView.attachToProcess(processHandler)

        val executionResult = DefaultExecutionResult(consoleView, processHandler)

        // Set up rerun failed tests action
        val rerunAction = consoleProperties.createRerunFailedTestsAction(consoleView)
        if (rerunAction != null) {
            rerunAction.init(consoleProperties)
            if (consoleView is com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView) {
                rerunAction.setModelProvider { consoleView.resultsViewer }
            }
            executionResult.setRestartActions(rerunAction)
        }

        return executionResult
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
