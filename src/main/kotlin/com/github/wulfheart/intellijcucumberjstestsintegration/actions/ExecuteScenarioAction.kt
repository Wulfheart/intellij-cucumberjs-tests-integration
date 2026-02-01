package com.github.wulfheart.intellijcucumberjstestsintegration.actions

import com.github.wulfheart.intellijcucumberjstestsintegration.run.PluginRunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.ui.ConsoleView

class ExecuteScenarioAction(consoleView: ConsoleView):  AbstractRerunFailedTestsAction(consoleView){

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
        println("getRunProfile called!")
        return null
        TODO()
        return environment.runProfile as? PluginRunConfiguration as MyRunProfile?;
    }



}