package site.addzero.cargo.buddy.automod

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.MacroExpansionManager
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsProcMacroKind
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.ItemPointer
import org.rust.lang.core.resolve2.ModData
import org.rust.stdext.RsResult

class AzAutomodMacroExpansionManager(
    private val delegate: MacroExpansionManager,
) : MacroExpansionManager {
    override val indexableDirectory: VirtualFile?
        get() = delegate.indexableDirectory

    override fun isReady(): Boolean = delegate.isReady()

    override fun getExpansionFor(
        call: RsPossibleMacroCall,
    ): CachedValueProvider.Result<RsResult<MacroExpansion, GetMacroExpansionError>> {
        return AzAutomodRustMacroExpansion.tryExpand(call) {
            delegate.getExpansionFor(call)
        }
    }

    override fun getExpandedFrom(element: RsExpandedElement): RsPossibleMacroCall? {
        return delegate.getExpandedFrom(element)
    }

    override fun getIncludedFrom(file: RsFile): RsMacroCall? {
        return delegate.getIncludedFrom(file)
    }

    override fun getContextOfMacroCallExpandedFrom(stubParent: RsFile): PsiElement? {
        return delegate.getContextOfMacroCallExpandedFrom(stubParent)
    }

    override fun isExpansionFileOfCurrentProject(file: VirtualFile): Boolean {
        return delegate.isExpansionFileOfCurrentProject(file)
    }

    override fun getCrateForExpansionFile(file: VirtualFile): Int? {
        return delegate.getCrateForExpansionFile(file)
    }

    override fun reexpand() {
        delegate.reexpand()
    }

    override fun getExpansionFileByName(
        crate: Int,
        expansionFileId: String,
    ): VirtualFile? {
        return delegate.getExpansionFileByName(crate, expansionFileId)
    }

    override fun findMacroCallByPointer(
        macroPointer: ItemPointer,
        kind: RsProcMacroKind,
        modData: ModData,
        defMap: CrateDefMap,
        indexIfDerive: Int,
    ): RsPossibleMacroCall? {
        return delegate.findMacroCallByPointer(macroPointer, kind, modData, defMap, indexIfDerive)
    }

    override val macroExpansionMode: MacroExpansionMode
        get() = delegate.macroExpansionMode

    override fun setUnitTestExpansionModeAndDirectory(
        mode: MacroExpansionScope,
        cacheDirectory: String,
        clearCacheBeforeDispose: Boolean,
    ): Disposable {
        return delegate.setUnitTestExpansionModeAndDirectory(mode, cacheDirectory, clearCacheBeforeDispose)
    }

    override fun updateInUnitTestMode() {
        delegate.updateInUnitTestMode()
    }

    override fun setMacroExpansionEnabled(enabled: Boolean) {
        delegate.setMacroExpansionEnabled(enabled)
    }
}
