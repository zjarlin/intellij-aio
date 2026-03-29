package site.addzero.kcloud.idea.configcenter

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.components.JBTextField
import java.io.File
import javax.swing.JComponent

class KCloudConfigCenterAppConfigurable : Configurable {
    private val settings: KCloudConfigCenterAppSettings
        get() = KCloudConfigCenterAppSettings.getInstance()

    private val sqlitePathField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String {
        return "KCloud Config Center"
    }

    override fun createComponent(): JComponent {
        sqlitePathField.addBrowseFolderListener(
            "选择 SQLite 文件",
            "留空时按 CONFIG_CENTER_DB_PATH → 项目内 config-center.sqlite 或 apps/kcloud/config-center.sqlite → ~/.kcloud/config-center.sqlite 解析",
            null,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
        )
        return panel {
            group("存储") {
                row("SQLite 路径") {
                    cell(sqlitePathField)
                        .align(AlignX.FILL)
                        .comment("留空时使用默认解析规则")
                }
                row {
                    comment(
                        "加密模式仍沿用 CONFIG_CENTER_MASTER_KEY 环境变量或系统属性；IDEA 设置页当前只管理 SQLite 路径。",
                    )
                }
            }
        }.also {
            reset()
        }
    }

    override fun isModified(): Boolean {
        return sqlitePathField.text.trim() != settings.sqlitePath.trim()
    }

    override fun apply() {
        settings.sqlitePath = sqlitePathField.text.trim()
    }

    override fun reset() {
        sqlitePathField.text = settings.sqlitePath
    }
}

class KCloudConfigCenterProjectConfigurable(
    private val project: Project,
) : Configurable {
    private val settings: KCloudConfigCenterProjectSettings
        get() = KCloudConfigCenterProjectSettings.getInstance(project)

    private val namespaceField = JBTextField()
    private val profileField = JBTextField()

    override fun getDisplayName(): String {
        return "KCloud Config Center"
    }

    override fun createComponent(): JComponent {
        return panel {
            group("当前项目默认值") {
                row("项目/命名空间") {
                    cell(namespaceField)
                        .align(AlignX.FILL)
                        .comment("留空时默认使用当前 IntelliJ 项目名")
                }
                row("Profile") {
                    cell(profileField)
                        .align(AlignX.FILL)
                        .comment("Alt+Enter 提取和 Tool Window 过滤默认都走这里")
                }
            }
        }.also {
            reset()
        }
    }

    override fun isModified(): Boolean {
        return namespaceField.text.trim() != settings.namespace.trim() ||
            profileField.text.trim() != settings.profile.trim()
    }

    override fun apply() {
        settings.namespace = namespaceField.text.trim()
        settings.profile = profileField.text.trim().ifBlank { "default" }
    }

    override fun reset() {
        namespaceField.text = settings.namespace
        profileField.text = settings.profile.ifBlank { "default" }
    }
}

internal fun KCloudConfigCenterService.describeDatabase(project: Project): String {
    return resolveDatabaseFile(project).absolutePath
}

internal fun File.displayPath(): String {
    return absolutePath
}
