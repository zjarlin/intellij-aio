package site.addzero.cargo.buddy.automod

import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsPossibleMacroCall

object AzAutomodRustApiBridge {
    fun getDefMapService(project: Project): Any? {
        return invokeStatic(
            "org.rust.lang.core.resolve2.DefMapServiceKt",
            "getDefMapService",
            project,
        )
    }

    fun getRustPsiManager(project: Project): Any? {
        return invokeStatic(
            "org.rust.lang.core.psi.RsPsiManagerKt",
            "getRustPsiManager",
            project,
        )
    }

    fun getExpansionKnowingExpansionFile(
        call: RsPossibleMacroCall,
        file: RsFile,
    ): MacroExpansion? {
        return invokeStatic(
            "org.rust.lang.core.macros.MacroExpansionKt",
            "getExpansionKnowingExpansionFile",
            call,
            file,
        ) as? MacroExpansion
    }

    fun getModificationTrackersForMemExpansion(call: RsPossibleMacroCall): Array<Any> {
        val trackers = invokeStatic(
            "org.rust.lang.core.macros.MacroExpansionManagerKt",
            "getModificationTrackersForMemExpansion",
            call,
        ) as? Array<*> ?: return emptyArray()

        return trackers.filterNotNull().toTypedArray()
    }

    fun getContextToSetForExpansion(call: RsPossibleMacroCall): PsiElement? {
        return invokeStatic(
            "org.rust.lang.core.psi.ext.RsPossibleMacroCallKt",
            "getContextToSetForExpansion",
            call,
        ) as? PsiElement
    }

    fun setExpandedFrom(
        element: RsExpandedElement,
        call: RsPossibleMacroCall,
    ) {
        invokeStatic(
            "org.rust.lang.core.macros.MacroExpansionManagerKt",
            "setExpandedFrom",
            element,
            call,
        )
    }

    fun setExpandedElementContext(
        element: RsExpandedElement,
        context: RsElement,
    ) {
        invokeStatic(
            "org.rust.lang.core.macros.RsExpandedElementKt",
            "setExpandedElementContext",
            element,
            context,
        )
    }

    fun getContainingRsFileSkippingCodeFragments(element: PsiElement): RsFile? {
        return invokeStatic(
            "org.rust.lang.core.psi.ext.PsiElementKt",
            "getContainingRsFileSkippingCodeFragments",
            element,
        ) as? RsFile
    }

    private fun invokeStatic(
        className: String,
        methodName: String,
        vararg args: Any,
    ): Any? {
        val method = Class.forName(className).methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == args.size
        } ?: error("Rust API method is unavailable: $className.$methodName")

        return method.invoke(null, *args)
    }
}
