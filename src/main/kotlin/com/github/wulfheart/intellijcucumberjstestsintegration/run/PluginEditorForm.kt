package com.github.wulfheart.intellijcucumberjstestsintegration.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.util.NodePackageField
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptBundle
import org.jetbrains.plugins.cucumber.javascript.CucumberJavaScriptUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class PluginEditorForm(private val myProject: Project) :
    SettingsEditor<PluginRunConfiguration?>() {
    private val myMainPanel: JPanel? = null
    private var myFileField: JTextField? = null
    private var myCucumberArguments: JTextField? = null
    private var myNodeInterpreterComponent: JComponent? = null
    private var myCucumberPackageComponent: JComponent? = null
    private var myInterpreterField: NodeJsInterpreterField? = null
    private var myCucumberPackageField: NodePackageField? = null
    private var myWorkingDirectoryField: TextFieldWithBrowseButton? = null
    private var myEnvVariablesComponent: EnvironmentVariablesComponent? = null

    init {
        val var10000 = GherkinFileType.INSTANCE
        val fileToRunChooser = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(var10000)
            .withTitle(CucumberJavaScriptBundle.message("choose.gherkin.file.or.directory", *arrayOfNulls<Any>(0)))
        // this.myFileField!!.addBrowseFolderListener(myProject, fileToRunChooser)
        createUIComponents()
    }

    override fun resetEditorFrom(configuration: PluginRunConfiguration) {
        this.myFileField!!.setText(configuration.toRun.map { t -> t.toLineString() }.joinToString(", "))
        this.myCucumberArguments!!.setText(configuration.cucumberJsArguments)
        this.myWorkingDirectoryField!!.setText(configuration.workingDirectory)
        val interpreterRef = configuration.myInterpreterRef
        this.myInterpreterField!!.setInterpreterRef(interpreterRef)
        val interpreter = interpreterRef.resolve(this.myProject)
        val pkg = CucumberJavaScriptUtil.getCucumberPackage(this.myProject, interpreter)
        this.myCucumberPackageField!!.setSelected(pkg)
        this.myEnvVariablesComponent!!.setEnvData(configuration.myEnvData)
    }

    override fun applyEditorTo(configuration: PluginRunConfiguration) {
        configuration.cucumberJsArguments = this.myCucumberArguments!!.getText()
        configuration.myInterpreterRef = this.myInterpreterField!!.interpreterRef
        configuration.workingDirectory = this.myWorkingDirectoryField!!.text
        configuration.toRun =
            this.myFileField!!.text.split(",").map { s -> ToRunItem.fromLineString(s.trim()) }.toMutableList()
        CucumberJavaScriptUtil.setCucumberPackage(
            configuration.getProject(),
            this.myCucumberPackageField!!.getSelected()
        )
        configuration.myEnvData = this.myEnvVariablesComponent!!.getEnvData()
    }

    override fun createEditor(): JComponent {

        return panel {
            row("Files to run:") {
                cell(myFileField!!)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            row("Cucumber arguments:") {
                cell(myCucumberArguments!!)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            row("Working directory:") {
                cell(myWorkingDirectoryField!!)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            row("Node.js interpreter:") {
                cell(myInterpreterField!!)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            row("Cucumber.js package:") {
                cell(myCucumberPackageField!!)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }

            row {
                cell(myEnvVariablesComponent!!)
                    .align(AlignX.FILL)
            }
        }


    }

    private fun createUIComponents() {
        myFileField = JTextField()
        myCucumberArguments = JTextField()
        myEnvVariablesComponent = EnvironmentVariablesComponent()
        myInterpreterField = NodeJsInterpreterField(myProject, false)
        myCucumberPackageField = NodePackageField(
            myInterpreterField!!,
            CucumberJavaScriptUtil.PKG_DESCRIPTOR,
            null
        )
        myWorkingDirectoryField = createWorkingDirectory(myProject, this)
        // this.myInterpreterField = NodeJsInterpreterField(this.myProject, false)
        // this.myNodeInterpreterComponent = this.myInterpreterField
        // this.myCucumberPackageField =
        //     NodePackageField(this.myInterpreterField!!, CucumberJavaScriptUtil.PKG_DESCRIPTOR, null)
        // this.myCucumberPackageComponent = this.myCucumberPackageField
        // this.myWorkingDirectoryField = createWorkingDirectory(this.myProject, this)
    }

    companion object {
        private fun createWorkingDirectory(project: Project, parent: Disposable): TextFieldWithBrowseButton {
            val workingDir = TextFieldWithBrowseButton()
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            workingDir.addBrowseFolderListener(project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
            FileChooserFactory.getInstance().installFileCompletion(workingDir.getTextField(), descriptor, false, parent)
            return workingDir
        }
    }
}
