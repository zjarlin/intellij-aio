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
    private var mainPanel: JPanel? = null
    private var catalogBannerCheckbox: JBCheckBox? = null
    private var silentUpsertTomlCheckbox: JBCheckBox? = null
    private var dedupStrategyCombo: JComboBox<String>? = null
    private var mirrorCombo: JComboBox<String>? = null
    private var autoUpdateWrapperCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "Gradle Buddy"

    override fun createComponent(): JComponent {
        tasksTextArea = JBTextArea(10, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        catalogPathField = JBTextField(40).apply {
            toolTipText = "版本目录文件的相对路径，相对于项目根目录。例如：gradle/libs.versions.toml 或 checkouts/build-logic/gradle/libs.versions.toml"
        }

        val properties = PropertiesComponent.getInstance(project)
        val bannerCheckBox = JBCheckBox("Show Version Catalog Banner").apply {
            isSelected = !properties.getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        }
        catalogBannerCheckbox = bannerCheckBox

        val silentUpsertCheckBox = JBCheckBox("Smart completion: silent upsert to TOML (回显 libs.xxx.xxx)").apply {
            toolTipText = "启用后，在 .gradle.kts 中补全依赖时自动写入 libs.versions.toml 并回显 implementation(libs.xxx.xxx)"
            isSelected = GradleBuddySettingsService.getInstance(project).isSilentUpsertToml()
        }
        silentUpsertTomlCheckbox = silentUpsertCheckBox

        val dedupCombo = JComboBox(DefaultComboBoxModel(arrayOf("MAJOR_VERSION", "ALT_SUFFIX"))).apply {
            selectedItem = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()
            toolTipText = """
                <html>
                Normalize 同 group:artifact 不同版本冲突时的去重策略：<br><br>
                <b>MAJOR_VERSION</b>（默认）— 提取版本号第一个数字作为后缀<br>
                &nbsp;&nbsp;例: spring-boot-starter 有 2.7.18 和 3.2.0 两个版本<br>
                &nbsp;&nbsp;→ xxx-spring-boot-starter-v2<br>
                &nbsp;&nbsp;→ xxx-spring-boot-starter-v3<br>
                &nbsp;&nbsp;accessor: libs.xxx.spring.boot.starter.v2<br><br>
                <b>ALT_SUFFIX</b> — 使用 -alt, -alt2 后缀（版本高的排前面）<br>
                &nbsp;&nbsp;→ xxx-spring-boot-starter-alt<br>
                &nbsp;&nbsp;→ xxx-spring-boot-starter-alt2<br>
                &nbsp;&nbsp;accessor: libs.xxx.spring.boot.starter.alt
                </html>
            """.trimIndent()
        }
        dedupStrategyCombo = dedupCombo

        val dedupDescLabel = JBLabel(
            "<html><font color='gray' size='-2'>" +
            "MAJOR_VERSION: 2.7.18 → -v2, 3.2.0 → -v3 &nbsp;|&nbsp; " +
            "ALT_SUFFIX: -alt, -alt2, -alt3" +
            "</font></html>"
        )

        val mirrorNames = arrayOf("Tencent Cloud (腾讯云)", "Aliyun (阿里云)", "Gradle Official")
        val mirrorCb = JComboBox(DefaultComboBoxModel(mirrorNames)).apply {
            selectedIndex = GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
            toolTipText = "Gradle Wrapper 更新时的默认镜像。启动检查和一键更新都会使用此镜像。"
        }
        mirrorCombo = mirrorCb

        val autoUpdateCb = JBCheckBox("Auto-update Gradle Wrapper on project open (自动更新 Wrapper)").apply {
            toolTipText = "启用后，每次打开项目时自动检查并静默更新所有 gradle-wrapper.properties 到最新版本（使用上方镜像）"
            isSelected = GradleBuddySettingsService.getInstance(project).isAutoUpdateWrapper()
        }
        autoUpdateWrapperCheckbox = autoUpdateCb

        val resetButton = JButton("重置为默认").apply {
            addActionListener {
                tasksTextArea?.text = GradleBuddySettingsService.DEFAULT_TASKS.joinToString("\n")
                catalogPathField?.text = GradleBuddySettingsService.DEFAULT_VERSION_CATALOG_PATH
            }
        }

        mainPanel = catalogPathField?.let {
          FormBuilder.createFormBuilder()
            .addLabeledComponent("Default fallback tasks (one per line):", JBScrollPane(tasksTextArea))
            .addLabeledComponent("Version catalog path (relative to project root):", it)
            .addComponent(bannerCheckBox as JComponent)
            .addComponent(silentUpsertCheckBox as JComponent)
            .addLabeledComponent("Normalize dedup strategy (同 artifact 多版本冲突):", dedupCombo)
            .addComponent(dedupDescLabel)
            .addLabeledComponent("Gradle Wrapper preferred mirror:", mirrorCb)
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
        return currentTasks != savedTasks || currentPath != savedPath || showBanner != savedShowBanner || silentUpsert != savedSilentUpsert || dedupStrategy != savedDedupStrategy || mirrorIndex != savedMirrorIndex || autoUpdate != savedAutoUpdate
    }

    override fun apply() {
        val tasks = tasksTextArea?.text?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        GradleBuddySettingsService.getInstance(project).setDefaultTasks(tasks)

        val path = catalogPathField?.text?.trim() ?: ""
        if (path.isNotBlank()) {
            GradleBuddySettingsService.getInstance(project).setVersionCatalogPath(path)
        }

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
    }

    override fun reset() {
        val tasks = GradleBuddySettingsService.getInstance(project).getDefaultTasks()
        tasksTextArea?.text = tasks.joinToString("\n")
        catalogPathField?.text = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val showBanner = !PropertiesComponent.getInstance(project)
            .getBoolean(VersionCatalogNotificationSettings.BANNER_DISABLED_KEY, false)
        catalogBannerCheckbox?.isSelected = showBanner
        silentUpsertTomlCheckbox?.isSelected = GradleBuddySettingsService.getInstance(project).isSilentUpsertToml()
        dedupStrategyCombo?.selectedItem = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()
        mirrorCombo?.selectedIndex = GradleBuddySettingsService.getInstance(project).getPreferredMirrorIndex()
        autoUpdateWrapperCheckbox?.isSelected = GradleBuddySettingsService.getInstance(project).isAutoUpdateWrapper()
    }

    override fun disposeUIResources() {
        tasksTextArea = null
        catalogPathField = null
        mainPanel = null
        catalogBannerCheckbox = null
        silentUpsertTomlCheckbox = null
        dedupStrategyCombo = null
        mirrorCombo = null
        autoUpdateWrapperCheckbox = null
    }
}
