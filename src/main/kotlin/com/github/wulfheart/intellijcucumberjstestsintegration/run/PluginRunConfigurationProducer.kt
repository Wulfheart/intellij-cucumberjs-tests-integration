package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.lang.javascript.psi.util.JSProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableImpl
import org.jetbrains.plugins.cucumber.psi.impl.GherkinTableRowImpl

// Why do we even need this?
private fun hasParentCalledFeatures(directory: PsiDirectory?): Boolean {
    var directory = directory
    while (directory != null) {
        if (directory.name == "features") {
            return true
        }

        directory = directory.getParentDirectory()
    }

    return false
}

private fun getFileToRun(element: PsiElement): String {
    return getPath(getFileOrDirectoryToRun(element))
}

private fun getPath(fileOrDirectory: PsiFileSystemItem): String {
    return FileUtil.toSystemIndependentName(fileOrDirectory.getVirtualFile().getPath())
}

private fun getFileOrDirectoryToRun(element: PsiElement?): PsiFileSystemItem {
    val fileOrDirectory = checkNotNull(
        PsiTreeUtil.getParentOfType(
            element,
            PsiFileSystemItem::class.java,
            false
        ) as PsiFileSystemItem
    )

    return fileOrDirectory
}

private fun guessWorkingDirectory(project: Project, psiFileItem: PsiFileSystemItem): String? {
    val virtualFile = psiFileItem.virtualFile
    if (virtualFile == null) {
        return null
    } else {
        val packageJson = JSProjectUtil.findFileUpToContentRoot(project, virtualFile, *arrayOf("package.json"))
        if (packageJson != null) {
            val directory = packageJson.parent
            if (directory != null) {
                return directory.path
            }
        }

        return null
    }
}

class PluginRunConfigurationProducer : LazyRunConfigurationProducer<PluginRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return getInstance().configurationFactories.first()
    }

    override fun setupConfigurationFromContext(
        configuration: PluginRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        configuration.toRun = mutableListOf()
        val element = sourceElement.get()
        if (sourceElement.isNull || element == null) {
            return false
        }
        if (element !is PsiDirectory && element !is GherkinFile) {
            return handleSingleFileThings(configuration, element, context)
        }
        return handleFile(configuration, element)
        // Might need to be changed later


    }

    private fun handleFile(configuration: PluginRunConfiguration, element: PsiElement): Boolean {
        val container = getFileOrDirectoryToRun(element)
        val workingDir =
            guessWorkingDirectory(configuration.project, container)
        if (workingDir != null) {
            configuration.workingDirectory = workingDir
        }

        configuration.name = container.name

        val path = when {
            element.containingFile is GherkinFile -> element.containingFile.virtualFile.path
            element is PsiDirectory -> element.virtualFile.path
            else -> throw Exception("Unsupported element type: ${element.javaClass.name}")
        }
        configuration.addFilePath(path)
        return true
    }

    fun handleSingleFileThings(
        configuration: PluginRunConfiguration,
        element: PsiElement,
        context: ConfigurationContext
    ): Boolean {
        val fileOrDirectory = getFileOrDirectoryToRun(element)
        val workingDir = guessWorkingDirectory(
            configuration.project,
            fileOrDirectory
        )
        if (workingDir !== null) {
            configuration.workingDirectory = workingDir
        }

        val tokenType = PsiUtilCore.getElementType(element)
        val configurationPrefix = when {
            tokenType == GherkinTokenTypes.SCENARIO_KEYWORD -> "Scenario: "
            tokenType == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD -> "Scenario Outline: "
            tokenType == GherkinTokenTypes.FEATURE_KEYWORD -> "Feature: "
            element is GherkinTableRowImpl -> "Example: "
            else -> throw Exception("Unsupported element type: ${element.javaClass.name}")
        }


        val scenarioName = when {
            tokenType == GherkinTokenTypes.SCENARIO_KEYWORD -> (element.context as GherkinStepsHolder).scenarioName
            tokenType == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD -> (element.context as GherkinStepsHolder).scenarioName
            tokenType == GherkinTokenTypes.FEATURE_KEYWORD -> (element.context as GherkinFeature).featureName
            element is GherkinTableRowImpl -> ((element.context as GherkinTableImpl).context?.context as GherkinStepsHolder).scenarioName
            else -> throw Exception("Unsupported element type: ${element.javaClass.name}")
        };
        configuration.name = configurationPrefix + StringUtil.shortenPathWithEllipsis(scenarioName, 30)

        val document = PsiDocumentManager.getInstance(configuration.project).getDocument(element.containingFile)!!
        val offset = element.textRange.startOffset

        val lineNumber = when {
            tokenType != GherkinTokenTypes.FEATURE_KEYWORD -> document.getLineNumber(offset) + 1 // 0-based
            else -> null
        }
        configuration.addFilePathAndLine(fileOrDirectory.virtualFile.path, lineNumber)

        return true;
    }

    override fun isConfigurationFromContext(
        configuration: PluginRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val element = context.psiLocation ?: return false

        // Build expected toRun items based on context
        val expectedItems = mutableListOf<ToRunItem>()

        if (element is PsiDirectory || element is GherkinFile) {
            // File or directory context
            val path = when {
                element.containingFile is GherkinFile -> element.containingFile.virtualFile?.path
                element is PsiDirectory -> element.virtualFile?.path
                else -> null
            } ?: return false
            expectedItems.add(ToRunItem(FileUtil.toSystemIndependentName(path), null))
        } else {
            // Single element context (scenario, feature keyword, table row, etc.)
            val fileOrDirectory = PsiTreeUtil.getParentOfType(element, PsiFileSystemItem::class.java, false)
                ?: return false
            val filePath = fileOrDirectory.virtualFile?.path ?: return false

            val tokenType = PsiUtilCore.getElementType(element)
            val document = PsiDocumentManager.getInstance(context.project).getDocument(element.containingFile)
                ?: return false

            val lineNumber = when {
                tokenType == GherkinTokenTypes.SCENARIO_KEYWORD ||
                tokenType == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD ||
                element is GherkinTableRowImpl -> {
                    document.getLineNumber(element.textRange.startOffset) + 1
                }
                tokenType == GherkinTokenTypes.FEATURE_KEYWORD -> null
                else -> return false
            }
            expectedItems.add(ToRunItem(FileUtil.toSystemIndependentName(filePath), lineNumber))
        }

        // Compare configuration's toRun with expected items
        return configuration.toRun == expectedItems
    }
}