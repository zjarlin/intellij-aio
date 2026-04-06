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
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

// Gradle Buddy 设置配置界面
class GradleBuddySettingsConfigurable(private val project: Project) : Configurable {

    private var tasksTextArea: JBTextArea? = null
    private var catalogPathField: JBTextField? = null
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

        catalogPathField = JBTextField(40).apply {
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
                catalogPathField?.text = GradleBuddySettingsService.DEFAULT_VERSION_CATALOG_PATH
                externalLibraryRepoField?.text = GradleBuddySettingsService.DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
            }
        }

        mainPanel = catalogPathField?.let { catalogField ->
          val externalRepoField = requireNotNull(externalLibraryRepoField)
          FormBuilder.createFormBuilder()
            .addLabeledComponent(GradleBuddyBundle.message("settings.language.label"), languageCb)
            .addLabeledComponent(GradleBuddyBundle.message("settings.default.tasks.label"), JBScrollPane(tasksTextArea))
            .addLabeledComponent(GradleBuddyBundle.message("settings.version.catalog.path.label"), catalogField)
            .addLabeledComponent(
                GradleBuddyBundle.message("settings.external.library.repo.path.label"),
                externalRepoField
            )
            .addComponent(bannerCheckBox as JComponent)
            .addComponent(silentUpsertCheckBox as JComponent)
            .addLabeledComponent(GradleBuddyBundle.message("settings.normalize.dedup.strategy.label"), dedupCombo)
            .addComponent(dedupDescLabel)
            .addLabeledComponent(GradleBuddyBundle.message("settings.wrapper.mirror.label"), mirrorCb)
            .addComponent(autoUpdateCb as JComponent)
        }
          ?.addComponent(resetButton)
          ?.addComponentFillVertically(JPanel(), 0)
            ?.panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val currentTasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val savedTasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        val currentPath = catalogPathField?.text ?: ""
        val savedPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val currentExternalRepoPath = externalLibraryRepoField?.text ?: ""
        val savedExternalRepoPath = GradleBuddySettingsService.getInstance(project).getExternalLibraryRepoPath()
        val showBanner = catalogBannerCheckbox?.isSelected ?: true
        val savedShowBanner = !PropertiesComponent.getInstance(project)
            .getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        val silentUpsert = silentUpsertTomlCheckbox?.isSelected ?: false
        val savedSilentUpsert = GradleBuddySettingsService.getInstance(project).isSilentUpsertToml()
        val dedupStrategy = dedupStrategyCombo?.selectedItem as? String ?: "MAJOR_VERSION"
        val savedDedupStrategy = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()
        val mirrorIndex = mirrorCombo?.selectedIndex ?: 0
        val savedMirrorIndex = GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
        val autoUpdate = autoUpdateWrapperCheckbox?.isSelected ?: false
        val savedAutoUpdate = GradleBuddySettingsService.getInstance(project).isAutoUpdateWrapper()
        val language = languageCombo?.selectedItem as? GradleBuddyLanguage ?: GradleBuddyLanguage.ZH
        val savedLanguage = GradleBuddyUiSettingsService.getInstance().getLanguage()
        return currentTasks != savedTasks ||
            currentPath != savedPath ||
            currentExternalRepoPath != savedExternalRepoPath ||
            showBanner != savedShowBanner ||
            silentUpsert != savedSilentUpsert ||
            dedupStrategy != savedDedupStrategy ||
            mirrorIndex != savedMirrorIndex ||
            autoUpdate != savedAutoUpdate ||
            language != savedLanguage
    }

    override fun apply() {
        val tasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        GradleBuddySettingsService.getInstance(project).setDefaultTasks(tasks)

        val path = catalogPathField?.text?.trim() ?: ""
        if (path.isNotBlank()) {
            GradleBuddySettingsService.getInstance(project).setVersionCatalogPath(path)
        }

        val externalRepoPath = externalLibraryRepoField?.text?.trim() ?: ""
        val normalizedExternalRepoPath = externalRepoPath.ifBlank {
            GradleBuddySettingsService.DEFAULT_EXTERNAL_LIBRARY_REPO_PATH
        }
        GradleBuddySettingsService.getInstance(project).setExternalLibraryRepoPath(normalizedExternalRepoPath)
        externalLibraryRepoField?.text = normalizedExternalRepoPath

        val showBanner = catalogBannerCheckbox?.isSelected ?: true
        PropertiesComponent.getInstance(project).setValue(
            VersionCatalogNotificationSettings.BANNER_DISABLED_KEY,
            !showBanner
        )

        val silentUpsert = silentUpsertTomlCheckbox?.isSelected ?: false
        GradleBuddySettingsService.getInstance(project).setSilentUpsertToml(silentUpsert)

        val dedupStrategy = dedupStrategyCombo?.selectedItem as? String ?: "MAJOR_VERSION"
        GradleBuddySettingsService.getInstance(project).setNormalizeDedupStrategy(dedupStrategy)

        val mirrorIndex = mirrorCombo?.selectedIndex ?: 0
        GradleBuddySettingsService.getInstance(project).setPreferredMirrorIndex(mirrorIndex)

        val autoUpdate = autoUpdateWrapperCheckbox?.isSelected ?: false
        GradleBuddySettingsService.getInstance(project).setAutoUpdateWrapper(autoUpdate)

        val language = languageCombo?.selectedItem as? GradleBuddyLanguage ?: GradleBuddyLanguage.ZH
        val uiSettings = GradleBuddyUiSettingsService.getInstance()
        val oldLanguage = uiSettings.getLanguage()
        uiSettings.setLanguage(language)
        if (language != oldLanguage) {
            GradleBuddyRegisteredActionI18n.refreshAll()
        }
    }

    override fun reset() {
        val tasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        tasksTextArea?.text = tasks.joinToString("\n")
        catalogPathField?.text = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        externalLibraryRepoField?.text = GradleBuddySettingsService.getInstance(project).getExternalLibraryRepoPath()
        val showBanner = !PropertiesComponent.getInstance(project)
            .getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        catalogBannerCheckbox?.isSelected = showBanner
        silentUpsertTomlCheckbox?.isSelected = GradleBuddySettingsService.getInstance(project).isSilentUpsertToml()
        dedupStrategyCombo?.selectedItem = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()
        mirrorCombo?.selectedIndex = GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
        autoUpdateWrapperCheckbox?.isSelected = GradleBuddySettingsService.getInstance(project).isAutoUpdateWrapper()
        languageCombo?.selectedItem = GradleBuddyUiSettingsService.getInstance().getLanguage()
    }

    override fun disposeUIResources() {
        tasksTextArea = null
        catalogPathField = null
        externalLibraryRepoField = null
        mainPanel = null
        catalogBannerCheckbox = null
        silentUpsertTomlCheckbox = null
        dedupStrategyCombo = null
        mirrorCombo = null
        autoUpdateWrapperCheckbox = null
        languageCombo = null
    }
}
