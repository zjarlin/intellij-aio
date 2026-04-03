package site.addzero.composebuddy.features.events

import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class EventsRefactor(
    private val project: Project,
) {
    fun apply(analysis: EventsAnalysisResult) {
        val function = analysis.function
        val factory = KtPsiFactory(project)
        val eventsTypeName = ComposeNamingSupport.uniqueTypeName(function, "${function.name ?: "Composable"}Events")
        val parameterOrder = function.valueParameters.mapNotNull { it.name }
        val callSites = ComposeCallSupport.collectCallSites(project, function)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.events.object")) {
            analysis.callbacks.forEach { callback ->
                ReferencesSearch.search(callback, LocalSearchScope(function.bodyExpression ?: function))
                    .findAll()
                    .mapNotNull { it.element as? KtNameReferenceExpression }
                    .forEach { reference ->
                        reference.replace(factory.createExpression("events.${callback.name}"))
                    }
            }
            val moved = analysis.callbacks.toSet()
            val newParameterTexts = buildList {
                add("events: $eventsTypeName")
                addAll(function.valueParameters.filterNot { it in moved }.map { it.text })
            }
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newFunction = function.replace(
                factory.createFunction(function.text.replace(oldParameterList, "(${newParameterTexts.joinToString(", ")})"))
            ) as KtNamedFunction
            val declaration = buildString {
                appendLine("private data class $eventsTypeName(")
                appendLine(analysis.callbacks.joinToString(",\n") { "    val ${it.name}: ${it.typeReference?.text ?: "() -> Unit"}" })
                appendLine(")")
            }
            newFunction.parent.addBefore(factory.createDeclaration(declaration.trim()), newFunction)
            newFunction.parent.addBefore(factory.createWhiteSpace("\n\n"), newFunction)
            callSites.forEach { call ->
                val argumentMap = ComposeCallSupport.extractArgumentMap(call, parameterOrder)
                val newArguments = buildList {
                    val eventArgs = analysis.callbacks.mapNotNull { callback ->
                        callback.name?.let { name -> argumentMap[name]?.let { "$name = $it" } }
                    }
                    add("events = $eventsTypeName(${eventArgs.joinToString(", ")})")
                    function.valueParameters.filterNot { it in moved }.forEach { parameter ->
                        parameter.name?.let { name -> argumentMap[name]?.let { add("$name = $it") } }
                    }
                }
                call.replace(factory.createExpression("${function.name}(${newArguments.joinToString(", ")})"))
            }
        }
    }
}
