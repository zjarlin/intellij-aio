package site.addzero.composeblocks.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composeblocks.model.ComposeBlockNode
import site.addzero.composeblocks.parser.ComposeBlockTreeBuilder
import java.awt.Color

private val TEXT_SESSION_KEY = Key.create<ComposeBlocksTextEditorSession>("compose.blocks.text.editor.session")

@Service(Service.Level.PROJECT)
class ComposeBlocksTextEditorService(
    private val project: Project,
) {

    fun installIfNeeded(
        file: VirtualFile,
        textEditor: TextEditor,
    ) {
        val editor = textEditor.editor as? EditorEx ?: return
        val parentDisposable = (textEditor as? Disposable) ?: (editor as? Disposable) ?: project
        installIfNeeded(file, editor, parentDisposable)
    }

    fun installIfNeeded(
        file: VirtualFile,
        editor: EditorEx,
        parentDisposable: Disposable,
    ) {
        if (!file.isComposeKotlinFile(project)) {
            return
        }

        val existingSession = editor.getUserData(TEXT_SESSION_KEY)
        if (existingSession != null) {
            existingSession.setProgressiveExpansionEnabled(file.isProgressiveExpansionEnabled())
            return
        }

        val session = ComposeBlocksTextEditorSession(project, file, editor)
        editor.putUserData(TEXT_SESSION_KEY, session)
        Disposer.register(parentDisposable, session)
        session.install()
        session.setProgressiveExpansionEnabled(file.isProgressiveExpansionEnabled())
    }

    fun updateProgressiveExpansion(
        editor: EditorEx,
        enabled: Boolean,
    ) {
        editor.getUserData(TEXT_SESSION_KEY)?.setProgressiveExpansionEnabled(enabled)
    }
}

private class ComposeBlocksTextEditorSession(
    private val project: Project,
    private val file: VirtualFile,
    private val editor: EditorEx,
) : Disposable {

    private val document = editor.document
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val decorationController = ComposeBlockDecorationController(document)
    private val managedFoldRegions = mutableListOf<FoldRegion>()

    private var visibleRoots: List<ComposeBlockNode> = emptyList()
    private var selectedNode: ComposeBlockNode? = null
    private var semanticRanges: List<ComposeInlineSemanticRange> = emptyList()
    private var progressiveExpansionEnabled = false
    private var refreshInFlight = false
    private var pendingDaemonRefresh = false
    private var daemonListenerInstalled = false

    fun install() {
        installDaemonListener()
        document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    scheduleRefresh(waitForDaemon = true)
                }
            },
            this,
        )

        editor.caretModel.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    syncSelectionFromCaret()
                }
            },
            this,
        )

        scheduleRefresh(waitForDaemon = true)
    }

    override fun dispose() {
        editor.putUserData(TEXT_SESSION_KEY, null)
        refreshAlarm.cancelAllRequests()
        decorationController.clear()
        editor.foldingModel.runBatchFoldingOperation {
            clearManagedFoldRegionsInCurrentBatch()
        }
    }

    fun setProgressiveExpansionEnabled(enabled: Boolean) {
        if (progressiveExpansionEnabled == enabled) {
            return
        }
        progressiveExpansionEnabled = enabled
        applyPresentation()
    }

    private fun scheduleRefresh(waitForDaemon: Boolean = false) {
        refreshAlarm.cancelAllRequests()
        if (waitForDaemon) {
            pendingDaemonRefresh = true
            refreshAlarm.addRequest(
                { forceRefreshAfterDaemonTimeout() },
                DAEMON_FALLBACK_DELAY_MS,
            )
        }
        refreshAlarm.addRequest(
            { scheduleRefreshIfReady() },
            180,
        )
    }

    private fun scheduleRefreshIfReady() {
        if (refreshInFlight) {
            return
        }
        if (!isReadyForBlocks()) {
            return
        }
        refreshModelAsync()
    }

    private fun isReadyForBlocks(): Boolean {
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart { scheduleRefreshIfReady() }
            return false
        }
        if (pendingDaemonRefresh) {
            return false
        }
        return true
    }

    private fun installDaemonListener() {
        if (daemonListenerInstalled) {
            return
        }
        daemonListenerInstalled = true
        project.messageBus.connect(this).subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished() {
                    if (pendingDaemonRefresh) {
                        pendingDaemonRefresh = false
                        scheduleRefresh()
                    }
                }
            },
        )
    }

    private fun forceRefreshAfterDaemonTimeout() {
        if (!pendingDaemonRefresh) {
            return
        }
        pendingDaemonRefresh = false
        scheduleRefresh()
    }

    private fun refreshModelAsync() {
        refreshInFlight = true
        ReadAction.nonBlocking<Snapshot?> {
            buildSnapshot()
        }.finishOnUiThread(ModalityState.any()) { snapshot ->
            refreshInFlight = false
            if (snapshot == null) {
                clearPresentation()
                return@finishOnUiThread
            }
            visibleRoots = snapshot.roots
            semanticRanges = snapshot.semanticRanges
            if (visibleRoots.isEmpty()) {
                clearPresentation()
                return@finishOnUiThread
            }
            selectedNode = findBestSelection(editor.caretModel.offset)
            applyPresentation()
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun buildSnapshot(): Snapshot? {
        val ktFile = createSnapshotKtFile() ?: return null
        val roots = ComposeBlockTreeBuilder.build(ktFile, hideShells = false)
        if (roots.isEmpty()) {
            return Snapshot(emptyList(), emptyList())
        }
        val ranges = collectSemanticRanges(ktFile)
        return Snapshot(roots, ranges)
    }

    private fun syncSelectionFromCaret() {
        if (visibleRoots.isEmpty()) {
            return
        }

        val nextSelection = findBestSelection(editor.caretModel.offset) ?: return
        if (nextSelection.id != selectedNode?.id) {
            selectedNode = nextSelection
        }
        applyPresentation()
    }

    private fun applyPresentation() {
        val node = selectedNode ?: run {
            clearPresentation()
            return
        }
        val selectedPath = findNodePath(node.id).orEmpty()
        val parentNode = selectedPath.dropLast(1).lastOrNull()
        decorationController.apply(
            editor = editor,
            selectedNode = node,
            parentNode = parentNode,
            selectedPath = selectedPath,
            caretOffset = editor.caretModel.offset,
            semanticRanges = semanticRanges,
        )
        if (progressiveExpansionEnabled) {
            updateFoldRegions(selectedPath.map { it.id }.toSet())
        } else {
            editor.foldingModel.runBatchFoldingOperation {
                clearManagedFoldRegionsInCurrentBatch()
            }
        }
    }

    private fun updateFoldRegions(expandedIds: Set<String>) {
        val ranges = mutableListOf<Pair<Int, Int>>()
        collectCollapsedRanges(visibleRoots, expandedIds, ranges)
        editor.foldingModel.runBatchFoldingOperation {
            clearManagedFoldRegionsInCurrentBatch()
            ranges.forEach { (startOffset, endOffset) ->
                if (startOffset >= endOffset) {
                    return@forEach
                }
                val region = editor.foldingModel.addFoldRegion(startOffset, endOffset, " … ")
                    ?: return@forEach
                region.isExpanded = false
                managedFoldRegions += region
            }
        }
    }

    private fun collectCollapsedRanges(
        nodes: List<ComposeBlockNode>,
        expandedIds: Set<String>,
        ranges: MutableList<Pair<Int, Int>>,
    ) {
        nodes.forEach { node ->
            val contentRange = node.contentRange
            if (contentRange == null || node.children.isEmpty()) {
                return@forEach
            }
            if (node.id in expandedIds) {
                collectCollapsedRanges(node.children, expandedIds, ranges)
            } else {
                ranges += contentRange.startOffset to contentRange.endOffset
            }
        }
    }

    private fun clearPresentation() {
        selectedNode = null
        decorationController.clear()
        editor.foldingModel.runBatchFoldingOperation {
            clearManagedFoldRegionsInCurrentBatch()
        }
    }

    private fun clearManagedFoldRegionsInCurrentBatch() {
        managedFoldRegions.forEach { region ->
            if (region.isValid) {
                editor.foldingModel.removeFoldRegion(region)
            }
        }
        managedFoldRegions.clear()
    }

    private fun findBestSelection(caretOffset: Int): ComposeBlockNode? {
        val allNodes = flattenNodes(visibleRoots)
        val byCaret = allNodes
            .filter { it.focusRange.contains(caretOffset) }
            .minByOrNull { it.renderRange.length }
        if (byCaret != null) {
            return byCaret
        }

        val previousId = selectedNode?.id
        if (previousId != null) {
            val previousNode = allNodes.firstOrNull { it.id == previousId }
            if (previousNode != null) {
                return previousNode
            }
        }

        return allNodes.firstOrNull()
    }

    private fun flattenNodes(nodes: List<ComposeBlockNode>): List<ComposeBlockNode> {
        return nodes.flatMap { node ->
            listOf(node) + flattenNodes(node.children)
        }
    }

    private fun findNodePath(
        nodeId: String,
        nodes: List<ComposeBlockNode> = visibleRoots,
        trail: List<ComposeBlockNode> = emptyList(),
    ): List<ComposeBlockNode>? {
        nodes.forEach { node ->
            val nextTrail = trail + node
            if (node.id == nodeId) {
                return nextTrail
            }
            val childResult = findNodePath(nodeId, node.children, nextTrail)
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun createSnapshotKtFile(): KtFile? {
        return PsiFileFactory.getInstance(project)
            .createFileFromText(
                file.name,
                KotlinFileType.INSTANCE,
                document.text,
            ) as? KtFile
    }

    private fun collectSemanticRanges(ktFile: KtFile): List<ComposeInlineSemanticRange> {
        val result = mutableListOf<ComposeInlineSemanticRange>()
        ktFile.collectDescendantsOfType<KtNamedFunction>()
            .filter { function ->
                function.annotationEntries.any { annotation ->
                    annotation.shortName?.asString() == "Composable"
                }
            }
            .forEach { function ->
                function.valueParameters.forEach { parameter ->
                    classifyParameter(parameter)?.let { color ->
                        result += ComposeInlineSemanticRange(
                            startOffset = parameter.textRange.startOffset,
                            endOffset = parameter.textRange.endOffset,
                            color = color,
                        )
                    }
                }
            }
        return result
    }

    private fun classifyParameter(parameter: KtParameter): Color? {
        val name = parameter.name.orEmpty()
        val typeText = parameter.typeReference?.text.orEmpty()
        val normalized = "$name $typeText".lowercase()
        return when {
            normalized.contains("state") -> Color(74, 139, 216, 36)
            normalized.contains("event") || normalized.contains("action") || normalized.contains("intent") -> Color(214, 132, 42, 34)
            else -> null
        }
    }
}

private data class Snapshot(
    val roots: List<ComposeBlockNode>,
    val semanticRanges: List<ComposeInlineSemanticRange>,
)

private const val DAEMON_FALLBACK_DELAY_MS = 1200
