package site.addzero.composebuddy.features.uistate

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class UiStateRefactor(
    private val project: Project,
) {
    fun apply(analysis: UiStateAnalysisResult) {
        val function = analysis.function
        val parameterOrder = function.valueParameters.mapNotNull { it.name }
        val callSites = ComposeCallSupport.collectCallSites(project, function)
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.minimal.ui.state")) {
            val candidateByName = analysis.candidates.associateBy { it.parameter.name }
            val newParameterTexts = buildList {
                function.valueParameters.forEach { parameter ->
                    val name = parameter.name ?: return@forEach
                    val candidate = candidateByName[name]
                    if (candidate == null) {
                        add(parameter.text)
                    } else {
                        val typeName = ComposeNamingSupport.uniqueTypeName(function, "${function.name ?: "Composable"}${name.replaceFirstChar { it.uppercase() }}UiState")
                        add("${name}: $typeName")
                    }
                }
            }
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val updatedText = function.text.replace(oldParameterList, "(${newParameterTexts.joinToString(", ")})")
            val newFunction = function.replace(factory.createFunction(updatedText)) as KtNamedFunction

            analysis.candidates.forEach { candidate ->
                val parameterName = candidate.parameter.name ?: return@forEach
                val typeName = ComposeNamingSupport.uniqueTypeName(newFunction, "${function.name ?: "Composable"}${parameterName.replaceFirstChar { it.uppercase() }}UiState")
                val declaration = buildString {
                    appendLine("private data class $typeName(")
                    appendLine(candidate.properties.joinToString(",\n") { "    val ${it.propertyName}: ${it.typeText}" })
                    appendLine(")")
                }
                newFunction.parent.addBefore(factory.createDeclaration(declaration.trim()), newFunction)
                newFunction.parent.addBefore(factory.createWhiteSpace("\n\n"), newFunction)
            }

            val body = newFunction.bodyExpression
            body?.collectDescendantsOfType<KtDotQualifiedExpression>()
                ?.sortedByDescending { it.textRange.startOffset }
                ?.forEach { expression ->
                    val receiver = expression.receiverExpression.text
                    val selector = expression.selectorExpression?.text ?: return@forEach
                    val candidate = candidateByName[receiver] ?: return@forEach
                    if (candidate.properties.none { it.propertyName == selector }) return@forEach
                    expression.replace(factory.createExpression("$receiver.$selector"))
                }

            callSites.forEach { call ->
                val argumentMap = ComposeCallSupport.extractArgumentMap(call, parameterOrder)
                val newArguments = buildList {
                    function.valueParameters.forEach { parameter ->
                        val name = parameter.name ?: return@forEach
                        val candidate = candidateByName[name]
                        if (candidate == null) {
                            argumentMap[name]?.let { add("$name = $it") }
                        } else {
                            val typeName = ComposeNamingSupport.uniqueTypeName(newFunction, "${function.name ?: "Composable"}${name.replaceFirstChar { it.uppercase() }}UiState")
                            val stateArgs = candidate.properties.joinToString(", ") { property ->
                                "${property.propertyName} = ${wrapReceiver(argumentMap[name] ?: name)}.${property.propertyName}"
                            }
                            add("$name = $typeName($stateArgs)")
                        }
                    }
                }
                call.replace(factory.createExpression("${function.name}(${newArguments.joinToString(", ")})"))
            }
        }
    }

    private fun wrapReceiver(text: String): String {
        return if (text.matches(Regex("[A-Za-z_][A-Za-z0-9_\\.]*"))) text else "($text)"
    }
}
