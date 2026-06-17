package site.addzero.cargo.buddy.automod

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.stdext.RsResult
import kotlin.io.path.Path

object AzAutomodRustMacroExpansion {
    private val LOG = Logger.getInstance(AzAutomodRustMacroExpansion::class.java)

    fun tryExpand(
        call: RsPossibleMacroCall,
        delegate: () -> CachedValueProvider.Result<RsResult<MacroExpansion, GetMacroExpansionError>>,
    ): CachedValueProvider.Result<RsResult<MacroExpansion, GetMacroExpansionError>> {
        if (!isAzAutomodDirCall(call)) {
            return delegate()
        }

        return try {
            val crate = site.addzero.cargo.buddy.model.CargoCrateResolver.resolveForFile(
                call.project,
                call.containingFile.virtualFile,
            ) ?: return delegate()

            val expandedText = AzAutomodExpander.expandMacroCallText(
                source = call.text,
                manifestDirectory = Path(crate.rootPath),
            )
            val file = RsPsiFactory(call.project, true, false).createFile(expandedText)
            val expansion = AzAutomodRustApiBridge.getExpansionKnowingExpansionFile(call, file)
                ?: return delegate()
            markExpansionContext(call, expansion)

            CachedValueProvider.Result(
                RsResult.Ok<MacroExpansion>(expansion),
                *AzAutomodRustApiBridge.getModificationTrackersForMemExpansion(call),
            )
        } catch (e: AzAutomodExpansionException) {
            LOG.warn("az-automod expansion failed: ${e.message}")
            delegate()
        } catch (e: Throwable) {
            LOG.warn("Unable to provide az-automod Rust macro expansion", e)
            delegate()
        }
    }

    private fun isAzAutomodDirCall(call: RsPossibleMacroCall): Boolean {
        return call.path?.text?.replace(" ", "") == "automod::dir" &&
            AzAutomodExpander.hasSingleAutomodMacroCall(call.text)
    }

    private fun markExpansionContext(
        call: RsPossibleMacroCall,
        expansion: MacroExpansion,
    ) {
        val context = AzAutomodRustApiBridge.getContextToSetForExpansion(call) as? RsElement
        for (element in expansion.elements) {
            AzAutomodRustApiBridge.setExpandedFrom(element, call)
            if (context != null) {
                AzAutomodRustApiBridge.setExpandedElementContext(element, context)
            }
        }

        val originalFile = AzAutomodRustApiBridge.getContainingRsFileSkippingCodeFragments(call as PsiElement)
        if (originalFile is RsFile) {
            expansion.file.inheritCachedDataFrom(originalFile, true)
        }
    }
}
