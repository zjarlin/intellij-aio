package site.addzero.smart.intentions.kotlin.entityfieldmerge

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal object EntityFieldCollector {
    fun collect(klass: KtClass): EntityFieldClassModel? {
        if (!isSupportedClass(klass)) {
            return null
        }

        val importMap = collectImportMap(klass.containingKtFile)
        val fields = buildList {
            klass.primaryConstructorParameters
                .filter { parameter -> parameter.hasValOrVar() }
                .mapNotNull { parameter -> parameter.toFieldModel(klass, importMap) }
                .forEach(::add)

            klass.body?.declarations
                .orEmpty()
                .filterIsInstance<KtProperty>()
                .filter { property -> property.containingClass() == klass }
                .mapNotNull { property -> property.toFieldModel(klass, importMap) }
                .forEach(::add)
        }.distinctBy { field -> field.name }

        if (fields.isEmpty()) {
            return null
        }
        return EntityFieldClassModel(klass = klass, fields = fields)
    }

    private fun isSupportedClass(klass: KtClass): Boolean {
        if (klass.name.isNullOrBlank()) {
            return false
        }
        if (klass.isEnum() || klass.isAnnotation()) {
            return false
        }
        return true
    }

    private fun KtParameter.toFieldModel(
        klass: KtClass,
        importMap: Map<String, String>,
    ): EntityFieldModel? {
        val fieldName = name?.takeIf { it.isNotBlank() } ?: return null
        val typeText = typeReference?.text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val keyword = if (isMutable) {
            "var"
        } else {
            "val"
        }
        val modifiers = renderSupportedModifiers(this)
        val annotations = annotationEntries.map { annotation -> annotation.text.trim() }
        val declarationLine = listOfNotNull(
            modifiers.takeIf { it.isNotBlank() },
            "$keyword $fieldName: $typeText",
        ).joinToString(" ")
        val declarationText = (annotations + declarationLine).joinToString("\n")

        return EntityFieldModel(
            name = fieldName,
            typeText = typeText,
            declarationText = declarationText,
            importFqNames = declarationText.collectImportFqNames(importMap),
            sourceClassName = klass.name.orEmpty(),
            origin = EntityFieldOrigin.PRIMARY_CONSTRUCTOR,
            parameter = this,
        )
    }

    private fun KtProperty.toFieldModel(
        klass: KtClass,
        importMap: Map<String, String>,
    ): EntityFieldModel? {
        val fieldName = name?.takeIf { it.isNotBlank() } ?: return null
        val typeText = typeReference?.text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val keyword = if (isVar) {
            "var"
        } else {
            "val"
        }
        val modifiers = renderSupportedModifiers(this)
        val annotations = annotationEntries.map { annotation -> annotation.text.trim() }
        val declarationLine = listOfNotNull(
            modifiers.takeIf { it.isNotBlank() },
            "$keyword $fieldName: $typeText",
        ).joinToString(" ")
        val declarationText = (annotations + declarationLine).joinToString("\n")

        return EntityFieldModel(
            name = fieldName,
            typeText = typeText,
            declarationText = declarationText,
            importFqNames = declarationText.collectImportFqNames(importMap),
            sourceClassName = klass.name.orEmpty(),
            origin = EntityFieldOrigin.BODY_PROPERTY,
            property = this,
        )
    }

    private fun renderSupportedModifiers(owner: KtModifierListOwner): String {
        return buildList {
            if (owner.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                add("override")
            }
            if (owner.hasModifier(KtTokens.LATEINIT_KEYWORD)) {
                add("lateinit")
            }
        }.joinToString(" ")
    }

    private fun collectImportMap(file: KtFile): Map<String, String> {
        return file.importDirectives
            .asSequence()
            .filter { directive -> !directive.isAllUnder }
            .mapNotNull { directive ->
                val fqName = directive.importedFqName?.asString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val alias = directive.aliasName?.takeIf { it.isNotBlank() }
                val shortName = alias ?: fqName.substringAfterLast('.')
                shortName to fqName
            }
            .toMap()
    }

    private fun String.collectImportFqNames(importMap: Map<String, String>): List<String> {
        return TYPE_IDENTIFIER_REGEX.findAll(this)
            .map { match -> match.value.substringAfterLast('.') }
            .mapNotNull { shortName -> importMap[shortName] }
            .distinct()
            .toList()
    }

    private val TYPE_IDENTIFIER_REGEX = Regex("\\b[A-Z][A-Za-z0-9_]*(?:\\.[A-Z][A-Za-z0-9_]*)*\\b")
}
