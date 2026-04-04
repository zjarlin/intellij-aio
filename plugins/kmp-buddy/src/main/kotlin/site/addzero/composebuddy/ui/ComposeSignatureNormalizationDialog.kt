package site.addzero.composebuddy.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeSignatureAnalysisResult
import site.addzero.composebuddy.refactor.ComposeRefactorRequest
import site.addzero.composebuddy.settings.ComposeBuddySettingsService
import javax.swing.JCheckBox
import javax.swing.JComponent

class ComposeSignatureNormalizationDialog(
    project: Project,
    private val analysis: ComposeSignatureAnalysisResult,
) : DialogWrapper(project) {
    private val settings = ComposeBuddySettingsService.getInstance().state
    private val extractPropsCheck = JCheckBox(ComposeBuddyBundle.message("dialog.normalize.props"), analysis.propsCandidates.isNotEmpty())
    private val extractEventsCheck = JCheckBox(ComposeBuddyBundle.message("dialog.normalize.events"), analysis.eventCandidates.isNotEmpty())
    private val extractStateCheck = JCheckBox(ComposeBuddyBundle.message("dialog.normalize.state"), analysis.statePairs.isNotEmpty())
    private val keepCompatibilityCheck = JCheckBox(ComposeBuddyBundle.message("dialog.normalize.keep.compat"), settings.keepCompatibilityByDefault)
    private var propsName = "${analysis.function.name}Props"
    private var eventsName = "${analysis.function.name}Events"
    private var stateName = "${analysis.function.name}State"

    init {
        title = ComposeBuddyBundle.message("dialog.normalize.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label(
                ComposeBuddyBundle.message(
                    "dialog.normalize.summary",
                    analysis.propsCandidates.size,
                    analysis.eventCandidates.size,
                    analysis.statePairs.size,
                )
            )
        }
        row {
            cell(extractPropsCheck)
        }
        row(ComposeBuddyBundle.message("dialog.normalize.props.name")) {
            textField().bindText(::propsName)
        }
        row {
            cell(extractEventsCheck)
        }
        row(ComposeBuddyBundle.message("dialog.normalize.events.name")) {
            textField().bindText(::eventsName)
        }
        row {
            cell(extractStateCheck)
        }
        row(ComposeBuddyBundle.message("dialog.normalize.state.name")) {
            textField().bindText(::stateName)
        }
        row {
            cell(keepCompatibilityCheck)
        }
    }

    fun buildRequest(): ComposeRefactorRequest {
        return ComposeRefactorRequest(
            extractProps = extractPropsCheck.isSelected,
            extractEvents = extractEventsCheck.isSelected,
            extractState = extractStateCheck.isSelected,
            propsTypeName = propsName,
            eventsTypeName = eventsName,
            stateTypeName = stateName,
            keepCompatibilityFunction = keepCompatibilityCheck.isSelected,
        )
    }
}
