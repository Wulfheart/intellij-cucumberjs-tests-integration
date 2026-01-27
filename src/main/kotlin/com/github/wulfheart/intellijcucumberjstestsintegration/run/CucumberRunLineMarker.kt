package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableHeaderRowImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableRowImpl

val RUN_LINE_MARKER_ELEMENTS = TokenSet.create(
    GherkinTokenTypes.FEATURE_KEYWORD,
    GherkinTokenTypes.SCENARIO_KEYWORD,
    GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD,
);


class CucumberRunLineMarker : RunLineMarkerContributor() {
    override fun getInfo(p0: PsiElement): Info? {
        // if (p0 !is LeafElement)
        //     return null

        val psiFile: PsiFile? = p0.containingFile
        if (psiFile !is GherkinFile)
            return null

        val type = PsiUtilCore.getElementType(p0.node)

        if ((!RUN_LINE_MARKER_ELEMENTS.contains(type) && p0 !is GherkinTableRowImpl) || p0 is GherkinTableHeaderRowImpl) {
            return null;
        }

        val state = this.getTestStateStorage(p0);
        return Info(getTestStateIcon(state, true), ExecutorAction.getActions(0), RUN_TEST_TOOLTIP_PROVIDER)
    }

    private fun getTestStateStorage(element: PsiElement): TestStateStorage.Record? {
        val url = element.getContainingFile().getVirtualFile().getUrl() + ":" + CucumberUtil.getLineNumber(element);
        return TestStateStorage.getInstance(element.getProject()).getState(url);
    }
}