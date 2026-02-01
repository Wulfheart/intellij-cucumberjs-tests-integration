package com.github.wulfheart.intellijcucumberjstestsintegration.actions

import com.github.wulfheart.intellijcucumberjstestsintegration.run.PluginRunConfiguration
import com.github.wulfheart.intellijcucumberjstestsintegration.run.ToRunItem
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module

class ExecuteScenarioAction(consoleView: ConsoleView) : AbstractRerunFailedTestsAction(consoleView) {

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
        val properties = myConsoleProperties ?: return null
        val project = properties.project

        // Get failed tests using the parent class method
        val failedTests = getFailedTests(project)
        if (failedTests.isEmpty()) return null

        // Extract locations from failed tests (test suites have the locationHint)
        val failedLocations = failedTests.mapNotNull { proxy ->
            proxy.locationUrl?.let { parseLocation(it) }
        }.distinctBy { "${it.filePath}:${it.line}" }

        if (failedLocations.isEmpty()) return null

        // Get original configuration
        val originalConfig = environment.runProfile as? PluginRunConfiguration ?: return null

        return MyRerunProfile(originalConfig, failedLocations)
    }

    private fun parseLocation(locationUrl: String): ToRunItem? {
        // Parse "file:///path/to/file.feature:123" or "file://path/to/file.feature:123"
        val filePrefix = "file://"
        if (!locationUrl.startsWith(filePrefix)) return null

        var pathWithLine = locationUrl.removePrefix(filePrefix)
        // Handle file:/// (three slashes for absolute paths on some systems)
        if (pathWithLine.startsWith("/") && pathWithLine.length > 1 && pathWithLine[1] == '/') {
            pathWithLine = pathWithLine.substring(1)
        }

        return ToRunItem.fromLineString(pathWithLine)
    }

    private class MyRerunProfile(
        private val originalConfig: PluginRunConfiguration,
        private val failedLocations: List<ToRunItem>
    ) : MyRunProfile(originalConfig) {

        override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
            // Clone the original configuration and replace the toRun list with failed locations
            val newConfig = originalConfig.clone() as PluginRunConfiguration
            newConfig.toRun.clear()
            failedLocations.forEach { loc ->
                newConfig.addFilePathAndLine(loc.filePath, loc.line)
            }
            newConfig.name = "Rerun Failed Scenarios"
            return newConfig.getState(executor, env)
        }

        override fun getModules(): Array<Module> = emptyArray()
    }
}