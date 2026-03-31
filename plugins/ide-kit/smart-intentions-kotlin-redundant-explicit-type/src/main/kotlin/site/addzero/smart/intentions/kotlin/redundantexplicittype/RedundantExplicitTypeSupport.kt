@file:OptIn(
    org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class,
    org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt::class,
)

package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KaCapturedType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaDynamicType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.codeinsight.utils.removeDeclarationTypeReference
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object RedundantExplicitTypeSupport {
    private val diagnosticFilter = KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS

    fun applicabilityRange(property: KtProperty): TextRange? {
        if (!isApplicable(property)) {
            return null
        }
        return TextRange(0, property.textLength)
    }

    fun isApplicable(property: KtProperty): Boolean {
        if (property.typeReference == null) {
            return false
        }

        return allowAnalysisOnEdt {
            evaluateApplicability(property)
        }
    }

    fun isApplicable(property: KtProperty, caretOffset: Int): Boolean {
        val range = applicabilityRange(property) ?: return false
        return range.containsOffset(caretOffset - property.textOffset)
    }

    fun apply(property: KtProperty) {
        property.removeDeclarationTypeReference()
    }

    private fun evaluateApplicability(property: KtProperty): Boolean {
        val typeReference = property.typeReference ?: return false
        if (typeReference.annotationEntries.isNotEmpty()) {
            return false
        }
        val originalState = analyze(property) {
            val declaredType = KaSessionReflectionBridge.getType(this, typeReference)
            if (!isStableType(declaredType)) {
                return@analyze null
            }

            val diagnostics = KaSessionReflectionBridge.diagnostics(this, property, diagnosticFilter)
            if (diagnostics.any { it.severity == KaSeverity.ERROR }) {
                return@analyze null
            }

            OriginalPropertyState(
                declaredTypePointer = declaredType.createPointer(),
                diagnostics = diagnostics.toSignatures(),
            )
        } ?: return false

        return when (evaluateApplicabilityWithCopy(property, originalState)) {
            CopyApplicability.APPLICABLE -> true
            CopyApplicability.NOT_APPLICABLE -> false
            CopyApplicability.UNAVAILABLE -> evaluateApplicabilityWithoutCopy(property, typeReference)
        }
    }

    private fun evaluateApplicabilityWithCopy(
        property: KtProperty,
        originalState: OriginalPropertyState,
    ): CopyApplicability {
        val copiedProperty = createAnalyzablePropertyCopyWithoutExplicitType(property) ?: run {
            return CopyApplicability.UNAVAILABLE
        }
        return analyze(copiedProperty) {
            val declaredType = KaSessionReflectionBridge.restore(this, originalState.declaredTypePointer) ?: run {
                return@analyze CopyApplicability.UNAVAILABLE
            }
            val inferredType = KaSessionReflectionBridge.getReturnType(this, copiedProperty)
            if (inferredType is KaErrorType) {
                return@analyze CopyApplicability.UNAVAILABLE
            }
            if (!isStableType(inferredType)) {
                return@analyze CopyApplicability.NOT_APPLICABLE
            }

            val diagnostics = KaSessionReflectionBridge.diagnostics(this, copiedProperty, diagnosticFilter)
            if (diagnostics.any { it.severity == KaSeverity.ERROR }) {
                return@analyze CopyApplicability.UNAVAILABLE
            }

            if (!originalState.diagnostics.containsAll(diagnostics.toSignatures())) {
                return@analyze CopyApplicability.UNAVAILABLE
            }

            if (KaSessionReflectionBridge.semanticallyEquals(this, declaredType, inferredType)) {
                CopyApplicability.APPLICABLE
            } else {
                CopyApplicability.NOT_APPLICABLE
            }
        }
    }

    private fun evaluateApplicabilityWithoutCopy(
        property: KtProperty,
        typeReference: KtTypeReference,
    ): Boolean {
        return analyze(property) {
            val initializer = property.initializerOrGetterInitializer() ?: run {
                return@analyze false
            }
            if (!K2RemoveExplicitTypeReflectionBridge.isInitializerTypeContextIndependent(this, initializer, typeReference)) {
                return@analyze false
            }

            val declaredType = KaSessionReflectionBridge.getType(this, typeReference)
            if (!isStableType(declaredType)) {
                return@analyze false
            }

            val inferredType = KaSessionReflectionBridge.getExpressionType(this, initializer) ?: run {
                return@analyze false
            }
            if (!isStableType(inferredType)) {
                return@analyze false
            }

            KaSessionReflectionBridge.semanticallyEquals(this, declaredType, inferredType)
        }
    }

    private fun createAnalyzablePropertyCopyWithoutExplicitType(property: KtProperty): KtProperty? {
        val originalFile = property.containingFile as? KtFile ?: return null
        val copiedFile = KtPsiFactory.contextual(originalFile, false).createFile(
            originalFile.name,
            originalFile.text,
        )
        val originalModule = KaModuleProvider.Companion.getModule(property.project, originalFile, null)
        KaProjectStructureReflectionBridge.setContextModule(copiedFile, originalModule)
        val copiedProperty = copiedFile.findElementAt(property.textOffset)?.getNonStrictParentOfType<KtProperty>() ?: return null
        copiedProperty.removeDeclarationTypeReference()
        return copiedProperty
    }

    private fun KaSession.isStableType(type: KaType): Boolean {
        return when (type) {
            is KaErrorType,
            is KaFlexibleType,
            is KaDynamicType,
            is KaCapturedType,
            is KaIntersectionType,
            -> false

            is KaDefinitelyNotNullType -> isStableType(type.original)
            is KaFunctionType -> {
                val receiverType = if (type.hasReceiver) type.receiverType else null
                listOfNotNull(receiverType, type.returnType)
                    .plus(type.parameterTypes)
                    .all { nestedType -> isStableType(nestedType) }
            }

            is KaClassType -> {
                if (type.symbol is KaAnonymousObjectSymbol) {
                    return false
                }
                type.typeArguments.all { projection ->
                    projection is KaStarTypeProjection || projection.type?.let { nestedType -> isStableType(nestedType) } == true
                }
            }

            else -> true
        }
    }

    private fun KtProperty.initializerOrGetterInitializer(): KtExpression? {
        initializer?.let { return it }
        val getter = getter ?: return null
        if (getter.hasBlockBody()) {
            return null
        }
        return getter.bodyExpression
    }

    private data class OriginalPropertyState(
        val declaredTypePointer: org.jetbrains.kotlin.analysis.api.types.KaTypePointer<KaType>,
        val diagnostics: DiagnosticSignatureBag,
    )

    private data class DiagnosticSignature(
        val factoryName: String,
        val severity: KaSeverity,
    )

    private class DiagnosticSignatureBag(
        private val counts: Map<DiagnosticSignature, Int>,
    ) {
        fun containsAll(other: DiagnosticSignatureBag): Boolean {
            return other.counts.all { (signature, count) ->
                (counts[signature] ?: 0) >= count
            }
        }
    }

    private enum class CopyApplicability {
        APPLICABLE,
        NOT_APPLICABLE,
        UNAVAILABLE,
    }

    private fun Collection<org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi<*>>.toSignatures(): DiagnosticSignatureBag {
        return DiagnosticSignatureBag(
            groupingBy {
                DiagnosticSignature(
                    factoryName = it.factoryName,
                    severity = it.severity,
                )
            }.eachCount()
        )
    }

    private object KaSessionReflectionBridge {
        private val getTypeMethod = KaSession::class.java.getMethod("getType", KtTypeReference::class.java)
        private val getReturnTypeMethod = KaSession::class.java.getMethod("getReturnType", KtDeclaration::class.java)
        private val getExpressionTypeMethod = KaSession::class.java.getMethod("getExpressionType", KtExpression::class.java)
        private val diagnosticsMethod = KaSession::class.java.getMethod(
            "diagnostics",
            KtElement::class.java,
            KaDiagnosticCheckerFilter::class.java,
        )
        private val restoreMethod = KaSession::class.java.getMethod("restore", KaTypePointer::class.java)
        private val semanticallyEqualsMethod = KaSession::class.java.getMethod(
            "semanticallyEquals",
            KaType::class.java,
            KaType::class.java,
            KaSubtypingErrorTypePolicy::class.java,
        )

        fun getType(session: KaSession, typeReference: KtTypeReference): KaType {
            return getTypeMethod.invoke(session, typeReference) as KaType
        }

        fun getReturnType(session: KaSession, declaration: KtDeclaration): KaType {
            return getReturnTypeMethod.invoke(session, declaration) as KaType
        }

        fun getExpressionType(session: KaSession, expression: KtExpression): KaType? {
            return getExpressionTypeMethod.invoke(session, expression) as? KaType
        }

        fun diagnostics(
            session: KaSession,
            element: KtElement,
            filter: KaDiagnosticCheckerFilter,
        ): Collection<org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi<*>> {
            @Suppress("UNCHECKED_CAST")
            return diagnosticsMethod.invoke(session, element, filter) as Collection<org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi<*>>
        }

        fun restore(session: KaSession, pointer: KaTypePointer<KaType>): KaType? {
            return restoreMethod.invoke(session, pointer) as? KaType
        }

        fun semanticallyEquals(session: KaSession, left: KaType, right: KaType): Boolean {
            return semanticallyEqualsMethod.invoke(
                session,
                left,
                right,
                KaSubtypingErrorTypePolicy.STRICT,
            ) as Boolean
        }
    }

    private object KaProjectStructureReflectionBridge {
        private val setContextModuleMethod = Class.forName(
            "org.jetbrains.kotlin.analysis.api.projectStructure.DanglingFilesKt",
        ).getMethod(
            "setContextModule",
            KtFile::class.java,
            KaModule::class.java,
        )

        fun setContextModule(file: KtFile, module: KaModule) {
            setContextModuleMethod.invoke(null, file, module)
        }
    }

    private object K2RemoveExplicitTypeReflectionBridge {
        private val intentionClass = Class.forName(
            "org.jetbrains.kotlin.idea.k2.codeinsight.intentions.RemoveExplicitTypeIntention",
        )
        private val intentionInstance = intentionClass.getDeclaredConstructor().newInstance()
        private val method = intentionClass.getDeclaredMethod(
            "isInitializerTypeContextIndependent",
            KaSession::class.java,
            KtExpression::class.java,
            KtTypeReference::class.java,
        ).apply {
            isAccessible = true
        }

        fun isInitializerTypeContextIndependent(
            session: KaSession,
            initializer: KtExpression,
            typeReference: KtTypeReference,
        ): Boolean {
            return method.invoke(intentionInstance, session, initializer, typeReference) as Boolean
        }
    }
}
