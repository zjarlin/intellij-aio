package site.addzero.gradle.buddy.settings

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.i18n.GradleBuddyLanguage
import site.addzero.gradle.buddy.i18n.GradleBuddyRegisteredActionI18n
import site.addzero.gradle.buddy.i18n.GradleBuddyUiSettingsService
import site.addzero.gradle.buddy.notification.VersionCatalogNotificationSettings
import java.util.LinkedHashSet
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class GradleBuddySettingsConfigurable(private val project: Project) : Configurable {

    private var tasksTextArea: JBTextArea? = null
    private var catalogPathCombo: JComboBox<String>? = null
    private var catalogPathAutoLabel: String = ""
    private var externalLibraryRepoField: JBTextField? = null
    private var mainPanel: JPanel? = null
    private var catalogBannerCheckbox: JBCheckBox? = null
    private var silentUpsertTomlCheckbox: JBCheckBox? = null
    private var dedupStrategyCombo: JComboBox<String>? = null
    private var mirrorCombo: JComboBox<String>? = null
    private var autoUpdateWrapperCheckbox: JBCheckBox? = null
    private var languageCombo: JComboBox<GradleBuddyLanguage>? = null

    override fun getDisplayName(): String = GradleBuddyBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        tasksTextArea = JBTextArea(10, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        catalogPathCombo = JComboBox<String>().apply {
            isEditable = true
            toolTipText = GradleBuddyBundle.message("settings.version.catalog.path.tooltip")
        }

        externalLibraryRepoField = JBTextField(40).apply {
            toolTipText = GradleBuddyBundle.message("settings.external.library.repo.path.tooltip")
        }

        val properties = PropertiesComponent.getInstance(project)
        val bannerCheckBox = JBCheckBox(GradleBuddyBundle.message("settings.show.version.catalog.banner")).apply {
            isSelected = !properties.getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        }
        catalogBannerCheckbox = bannerCheckBox

        val silentUpsertCheckBox = JBCheckBox(GradleBuddyBundle.message("settings.silent.upsert.toml")).apply {
            toolTipText = GradleBuddyBundle.message("settings.silent.upsert.toml.tooltip")
            isSelected = GradleBuddySettingsService.getInstance(project).isSilentUpsertToml()
        }
        silentUpsertTomlCheckbox = silentUpsertCheckBox

        val languageCb = JComboBox(DefaultComboBoxModel(GradleBuddyLanguage.entries.toTypedArray())).apply {
            selectedItem = GradleBuddyUiSettingsService.getInstance().getLanguage()
            renderer = com.intellij.ui.SimpleListCellRenderer.create("") { value ->
                when (value) {
                    GradleBuddyLanguage.ZH -> GradleBuddyBundle.message("settings.language.zh")
                    GradleBuddyLanguage.EN -> GradleBuddyBundle.message("settings.language.en")
                    null -> ""
                }
            }
        }
        languageCombo = languageCb

        val dedupCombo = JComboBox(DefaultComboBoxModel(arrayOf("MAJOR_VERSION", "ALT_SUFFIX"))).apply {
            selectedItem = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()
            toolTipText = GradleBuddyBundle.message("settings.normalize.dedup.strategy.tooltip")
        }
        dedupStrategyCombo = dedupCombo

        val dedupDescLabel = JBLabel(GradleBuddyBundle.message("settings.normalize.dedup.strategy.desc"))

        val mirrorNames = arrayOf(
            GradleBuddyBundle.message("settings.wrapper.mirror.tencent"),
            GradleBuddyBundle.message("settings.wrapper.mirror.aliyun"),
            GradleBuddyBundle.message("settings.wrapper.mirror.official")
        )
        val mirrorCb = JComboBox(DefaultComboBoxModel(mirrorNames)).apply {
            selectedIndex = GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
            toolTipText = GradleBuddyBundle.message("settings.wrapper.mirror.tooltip")
        }
        mirrorCombo = mirrorCb

        val autoUpdateCb = JBCheckBox(GradleBuddyBundle.message("settings.auto.update.wrapper")).apply {
            toolTipText = GradleBuddyBundle.message("settings.auto.update.wrapper.tooltip")
            isSelected = GradleBuddySettingsService.getInstance(project).isAutoUpdateWrapper()
        }
        autoUpdateWrapperCheckbox = autoUpdateCb

        val resetButton = JButton(GradleBuddyBundle.message("settings.reset.defaults")).apply {
            addActionListener {
                tasksTextArea?.text = GradleBuddySettingsService.DEFAULT_TASKS.joinToString("\n")
                refreshCatalogPathOptions(null)
                externalLibraryRepoField?.text = GradleBuddySettingsService.DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
            }
        }

        refreshCatalogPathOptions(
            GradleBuddySettingsService.getInstance(project)
                .takeIf { it.isVersionCatalogPathCustomized() }
                ?.getConfiguredVersionCatalogPath()
        )

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(GradleBuddyBundle.message("settings.language.label"), languageCb)
            .addLabeledComponent(GradleBuddyBundle.message("settings.default.tasks.label"), JBScrollPane(tasksTextArea))
            .addLabeledComponent(GradleBuddyBundle.message("settings.version.catalog.path.label"), catalogPathCombo!!)
            .addLabeledComponent(
                GradleBuddyBundle.message("settings.external.library.repo.path.label"),
                externalLibraryRepoField!!
            )
            .addComponent(bannerCheckBox as JComponent)
            .addComponent(silentUpsertCheckBox as JComponent)
            .addLabeledComponent(GradleBuddyBundle.message("settings.normalize.dedup.strategy.label"), dedupCombo)
            .addComponent(dedupDescLabel)
            .addLabeledComponent(GradleBuddyBundle.message("settings.wrapper.mirror.label"), mirrorCb)
            .addComponent(autoUpdateCb as JComponent)
            .addComponent(resetButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = GradleBuddySettingsService.getInstance(project)
        val currentTasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val currentCatalogPathOverride = getSelectedCatalogPathOverride()
        val currentCatalogPathCustomized = !currentCatalogPathOverride.isNullOrBlank()
        val currentExternalRepoPath = externalLibraryRepoField?.text?.trim().orEmpty()
        val savedExternalRepoPath = settings.getExternalLibraryRepoPath().trim()
        val showBanner = catalogBannerCheckbox?.isSelected ?: true
        val savedShowBanner = !PropertiesComponent.getInstance(project)
            .getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        val silentUpsert = silentUpsertTomlCheckbox?.isSelected ?: false
        val dedupStrategy = dedupStrategyCombo?.selectedItem as? String ?: "MAJOR_VERSION"
        val mirrorIndex = mirrorCombo?.selectedIndex ?: 0
        val autoUpdate = autoUpdateWrapperCheckbox?.isSelected ?: false
        val language = languageCombo?.selectedItem as? GradleBuddyLanguage ?: GradleBuddyLanguage.ZH

        return currentTasks != settings.getDefaultTasks() ||
            currentCatalogPathCustomized != settings.isVersionCatalogPathCustomized() ||
            (currentCatalogPathCustomized && currentCatalogPathOverride != settings.getConfiguredVersionCatalogPath()) ||
            currentExternalRepoPath != savedExternalRepoPath ||
            showBanner != savedShowBanner ||
            silentUpsert != settings.isSilentUpsertToml() ||
            dedupStrategy != settings.getNormalizeDedupStrategy() ||
            mirrorIndex != settings.getPreferredMirrorIndex() ||
            autoUpdate != settings.isAutoUpdateWrapper() ||
            language != GradleBuddyUiSettingsService.getInstance().getLanguage()
    }

    override fun apply() {
        val settings = GradleBuddySettingsService.getInstance(project)
        val tasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        settings.setDefaultTasks(tasks)

        val catalogPathOverride = getSelectedCatalogPathOverride()
        if (catalogPathOverride.isNullOrBlank()) {
            settings.clearVersionCatalogPathOverride()
        } else {
            settings.setVersionCatalogPath(catalogPathOverride)
        }

        val externalRepoPath = externalLibraryRepoField?.text?.trim().orEmpty()
        val normalizedExternalRepoPath = externalRepoPath.ifBlank {
            GradleBuddySettingsService.DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
        }
        settings.setExternalLibraryRepoPath(normalizedExternalRepoPath)
        externalLibraryRepoField?.text = normalizedExternalRepoPath

        val showBanner = catalogBannerCheckbox?.isSelected ?: true
        PropertiesComponent.getInstance(project).setValue(
            VersionCatalogNotificationSettings.BANNER_DISABLED_KEY,
            !showBanner
        )

        val silentUpsert = silentUpsertTomlCheckbox?.isSelected ?: false
        settings.setSilentUpsertToml(silentUpsert)

        val dedupStrategy = dedupStrategyCombo?.selectedItem as? String ?: "MAJOR_VERSION"
        settings.setNormalizeDedupStrategy(dedupStrategy)

        val mirrorIndex = mirrorCombo?.selectedIndex ?: 0
        settings.setPreferredMirrorIndex(mirrorIndex)

        val autoUpdate = autoUpdateWrapperCheckbox?.isSelected ?: false
        settings.setAutoUpdateWrapper(autoUpdate)

        val language = languageCombo?.selectedItem as? GradleBuddyLanguage ?: GradleBuddyLanguage.ZH
        val uiSettings = GradleBuddyUiSettingsService.getInstance()
        val oldLanguage = uiSettings.getLanguage()
        uiSettings.setLanguage(language)
        if (language != oldLanguage) {
            GradleBuddyRegisteredActionI18n.refreshAll()
        }

        refreshCatalogPathOptions(
            settings.takeIf { it.isVersionCatalogPathCustomized() }?.getConfiguredVersionCatalogPath()
        )
    }

    override fun reset() {
        val settings = GradleBuddySettingsService.getInstance(project)
        tasksTextArea?.text = settings.getDefaultTasks().joinToString("\n")
        refreshCatalogPathOptions(
            settings.takeIf { it.isVersionCatalogPathCustomized() }?.getConfiguredVersionCatalogPath()
        )
        externalLibraryRepoField?.text = settings.getExternalLibraryRepoPath()

        val showBanner = !PropertiesComponent.getInstance(project)
            .getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        catalogBannerCheckbox?.isSelected = showBanner
        silentUpsertTomlCheckbox?.isSelected = settings.isSilentUpsertToml()
        dedupStrategyCombo?.selectedItem = settings.getNormalizeDedupStrategy()
        mirrorCombo?.selectedIndex = settings.getPreferredMirrorIndex()
        autoUpdateWrapperCheckbox?.isSelected = settings.isAutoUpdateWrapper()
        languageCombo?.selectedItem = GradleBuddyUiSettingsService.getInstance().getLanguage()
    }

    override fun disposeUIResources() {
        tasksTextArea = null
        catalogPathCombo = null
        catalogPathAutoLabel = ""
        externalLibraryRepoField = null
        mainPanel = null
        catalogBannerCheckbox = null
        silentUpsertTomlCheckbox = null
        dedupStrategyCombo = null
        mirrorCombo = null
        autoUpdateWrapperCheckbox = null
        languageCombo = null
    }

    private fun refreshCatalogPathOptions(selectedPathOverride: String?) {
        val settings = GradleBuddySettingsService.getInstance(project)
        val items = LinkedHashSet<String>()

        catalogPathAutoLabel = buildAutoDetectLabel(settings)
        items += catalogPathAutoLabel
        settings.getVersionCatalogPathCandidates(project).forEach(items::add)
        if (!selectedPathOverride.isNullOrBlank()) {
            items += selectedPathOverride
        }

        catalogPathCombo?.model = DefaultComboBoxModel(items.toTypedArray())
        catalogPathCombo?.selectedItem = selectedPathOverride ?: catalogPathAutoLabel
    }

    private fun buildAutoDetectLabel(settings: GradleBuddySettingsService): String {
        val effectivePath = settings.getEffectiveVersionCatalogPath(project)
        return if (effectivePath.isBlank()) {
            GradleBuddyBundle.message("settings.version.catalog.path.auto")
        } else {
            GradleBuddyBundle.message("settings.version.catalog.path.auto.detected", effectivePath)
        }
    }

    private fun getSelectedCatalogPathOverride(): String? {
        val rawValue = catalogPathCombo?.editor?.item?.toString()?.trim().orEmpty()
        return when {
            rawValue.isBlank() -> null
            rawValue == catalogPathAutoLabel -> null
            else -> rawValue
        }
    }
}
