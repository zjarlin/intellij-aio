package site.addzero.smart.intentions.kotlin.classtointerface

import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

internal object ClassToInterfaceSupport {
    fun isApplicable(klass: KtClass, caretOffset: Int): Boolean {
        val candidate = buildCandidate(klass) ?: return false
        val bodyStartOffset = klass.body?.lBrace?.textOffset ?: klass.textRange.endOffset
        return caretOffset <= bodyStartOffset && candidate.interfaceText.isNotBlank()
    }

    fun apply(klass: KtClass) {
        val candidate = buildCandidate(klass) ?: return
        val psiFactory = KtPsiFactory(klass.project)
        val replacement = psiFactory.createDeclaration<KtClass>(candidate.interfaceText)
        val replaced = klass.replace(replacement) as KtClass
        CodeStyleManager.getInstance(klass.project).reformat(replaced)
    }

    private fun buildCandidate(klass: KtClass): InterfaceCandidate? {
        val name = klass.name ?: return null
        if (!isSupportedContainer(klass)) {
            return null
        }
        if (klass.isInterface() || klass.isEnum() || klass.isAnnotation()) {
            return null
        }
        if (klass.hasModifier(KtTokens.INNER_KEYWORD) ||
            klass.hasModifier(KtTokens.SEALED_KEYWORD) ||
            klass.hasModifier(KtTokens.VALUE_KEYWORD)
        ) {
            return null
        }

        val body = klass.body
        if (body != null) {
            if (body.children.any { it is KtAnonymousInitializer }) {
                return null
            }
            if (body.declarations.any { declaration ->
                    declaration is KtSecondaryConstructor || declaration is KtProperty
                }
            ) {
                return null
            }
        }

        val constructorParameters = klass.primaryConstructorParameters
        if (constructorParameters.any { parameter -> !isSupportedConstructorProperty(parameter) }) {
            return null
        }

        if (klass.superTypeListEntries.any { entry ->
                entry is KtSuperTypeCallEntry || entry is KtDelegatedSuperTypeEntry
            }
        ) {
            return null
        }

        val interfaceText = buildInterfaceText(
            klass = klass,
            name = name,
            constructorProperties = constructorParameters,
            bodyDeclarations = body
                ?.declarations
                ?.filterNot { declaration -> declaration is KtSecondaryConstructor }
                .orEmpty(),
        )
        return InterfaceCandidate(interfaceText)
    }

    private fun isSupportedContainer(klass: KtClass): Boolean {
        return klass.parent is KtFile || klass.parent is KtClassBody
    }

    private fun isSupportedConstructorProperty(parameter: KtParameter): Boolean {
        if (!parameter.hasValOrVar()) {
            return false
        }
        if (parameter.typeReference == null) {
            return false
        }
        if (parameter.annotationEntries.isNotEmpty()) {
            return false
        }
        if (parameter.hasModifier(KtTokens.PRIVATE_KEYWORD) || parameter.hasModifier(KtTokens.PROTECTED_KEYWORD)) {
            return false
        }
        val unsupportedModifier = listOf(
            KtTokens.IN_KEYWORD,
            KtTokens.OUT_KEYWORD,
            KtTokens.CROSSINLINE_KEYWORD,
            KtTokens.NOINLINE_KEYWORD,
            KtTokens.LATEINIT_KEYWORD,
        ).any { modifier -> parameter.hasModifier(modifier) }
        return !unsupportedModifier
    }

    private fun buildInterfaceText(
        klass: KtClass,
        name: String,
        constructorProperties: List<KtParameter>,
        bodyDeclarations: List<KtDeclaration>,
    ): String {
        val prefixLines = buildList {
            klass.docComment?.text?.let(::add)
            addAll(klass.annotationEntries.map { it.text })
            renderClassModifiers(klass)?.let(::add)
        }
        val memberTexts = buildList {
            constructorProperties.forEach { parameter ->
                add(renderConstructorProperty(parameter))
            }
            bodyDeclarations.forEach { declaration ->
                add(declaration.text.trimIndent())
            }
        }

        return buildString {
            if (prefixLines.isNotEmpty()) {
                append(prefixLines.joinToString("\n"))
                append('\n')
            }
            append("interface ")
            append(name)
            append(klass.typeParameterList?.text.orEmpty())
            val superTypesText = klass.superTypeListEntries.joinToString(", ") { entry -> entry.text }
            if (superTypesText.isNotBlank()) {
                append(" : ")
                append(superTypesText)
            }
            klass.typeConstraintList?.text?.takeIf { it.isNotBlank() }?.let { constraints ->
                append(' ')
                append(constraints)
            }
            append(" {\n")
            if (memberTexts.isNotEmpty()) {
                append(memberTexts.joinToString("\n").prependIndent("    "))
                append('\n')
            }
            append('}')
        }
    }

    private fun renderClassModifiers(klass: KtClass): String? {
        val modifiers = buildList {
            renderVisibilityModifier(klass)?.let(::add)
            if (klass.hasModifier(KtTokens.EXPECT_KEYWORD)) {
                add("expect")
            }
            if (klass.hasModifier(KtTokens.ACTUAL_KEYWORD)) {
                add("actual")
            }
        }
        return modifiers.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun renderVisibilityModifier(klass: KtClass): String? {
        return when {
            klass.hasModifier(KtTokens.PRIVATE_KEYWORD) -> "private"
            klass.hasModifier(KtTokens.PROTECTED_KEYWORD) -> "protected"
            klass.hasModifier(KtTokens.INTERNAL_KEYWORD) -> "internal"
            klass.hasModifier(KtTokens.PUBLIC_KEYWORD) -> "public"
            else -> null
        }
    }

    private fun renderConstructorProperty(parameter: KtParameter): String {
        val modifiers = buildList {
            if (parameter.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                add("override")
            }
        }
        val keyword = if (parameter.isMutable) {
            "var"
        } else {
            "val"
        }
        val propertyCore = "$keyword ${parameter.name}: ${parameter.typeReference?.text.orEmpty()}"
        return (modifiers + propertyCore).joinToString(" ")
    }
}

private data class InterfaceCandidate(
    val interfaceText: String,
)
