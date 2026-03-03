package site.addzero.cloudfile.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.Composite
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JComponent

/**
 * Main settings configurable for Cloud File Hosting
 */
class CloudFileConfigurable : Configurable, Composite {

    private var mainPanel: JBTabbedPane? = null

    // Sub-configurables
    private val storageSettings = StorageSettingsPanel()
    private val globalRulesSettings = GlobalRulesPanel()
    private val customRulesSettings = CustomRulesPanel()

    override fun getDisplayName(): String = "Cloud File Hosting"

    override fun getConfigurables(): Array<Configurable> {
        return arrayOf(
            storageSettings,
            globalRulesSettings,
            customRulesSettings
        )
    }

    override fun createComponent(): JComponent {
        val tabbedPane = JBTabbedPane()

        tabbedPane.addTab("Storage", storageSettings.createComponent())
        tabbedPane.addTab("Global Rules", globalRulesSettings.createComponent())
        tabbedPane.addTab("Custom Rules", customRulesSettings.createComponent())

        mainPanel = tabbedPane
        return tabbedPane
    }

    override fun isModified(): Boolean {
        return storageSettings.isModified ||
               globalRulesSettings.isModified ||
               customRulesSettings.isModified
    }

    override fun apply() {
        storageSettings.apply()
        globalRulesSettings.apply()
        customRulesSettings.apply()
    }

    override fun reset() {
        storageSettings.reset()
        globalRulesSettings.reset()
        customRulesSettings.reset()
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

/**
 * Storage configuration panel
 */
class StorageSettingsPanel : Configurable {

    private val settings = CloudFileSettings.getInstance()
    private var panel: StorageConfigPanel? = null

    override fun getDisplayName(): String = "Storage Configuration"

    override fun createComponent(): JComponent {
        panel = StorageConfigPanel()
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        return panel?.isModified(settings) ?: false
    }

    override fun apply() {
        panel?.applyTo(settings)
    }

    override fun reset() {
        panel?.resetFrom(settings)
    }

    /**
     * Inner panel class for storage configuration UI
     */
    class StorageConfigPanel : com.intellij.ui.components.JBPanel<StorageConfigPanel>(java.awt.BorderLayout()) {

        private val providerCombo = com.intellij.openapi.ui.ComboBox(arrayOf("S3", "OSS"))
        private val s3EndpointField = com.intellij.ui.components.JBTextField()
        private val s3RegionField = com.intellij.ui.components.JBTextField()
        private val s3BucketField = com.intellij.ui.components.JBTextField()
        private val s3AccessKeyField = com.intellij.ui.components.JBPasswordField()
        private val s3SecretKeyField = com.intellij.ui.components.JBPasswordField()

        private val ossEndpointField = com.intellij.ui.components.JBTextField()
        private val ossBucketField = com.intellij.ui.components.JBTextField()
        private val ossAccessKeyIdField = com.intellij.ui.components.JBPasswordField()
        private val ossAccessKeySecretField = com.intellij.ui.components.JBPasswordField()

        private val autoSyncCheck = com.intellij.ui.components.JBCheckBox("Enable auto-sync")
        private val syncIntervalSpinner = javax.swing.JSpinner(javax.swing.SpinnerNumberModel(5, 1, 60, 1))
        private val encryptCacheCheck = com.intellij.ui.components.JBCheckBox("Encrypt local cache")

        init {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)

            // Provider selection
            add(createLabeledComponent("Provider:", providerCombo))
            add(javax.swing.Box.createVerticalStrut(10))

            // S3 Panel
            val s3Panel = com.intellij.ui.components.JBPanel<S3Panel>(java.awt.GridLayout(0, 2, 5, 5))
            s3Panel.border = javax.swing.border.TitledBorder("S3 Configuration")
            s3Panel.add(javax.swing.JLabel("Endpoint:"))
            s3Panel.add(s3EndpointField)
            s3Panel.add(javax.swing.JLabel("Region:"))
            s3Panel.add(s3RegionField)
            s3Panel.add(javax.swing.JLabel("Bucket:"))
            s3Panel.add(s3BucketField)
            s3Panel.add(javax.swing.JLabel("Access Key:"))
            s3Panel.add(s3AccessKeyField)
            s3Panel.add(javax.swing.JLabel("Secret Key:"))
            s3Panel.add(s3SecretKeyField)
            add(s3Panel)
            add(javax.swing.Box.createVerticalStrut(10))

            // OSS Panel
            val ossPanel = com.intellij.ui.components.JBPanel<OssPanel>(java.awt.GridLayout(0, 2, 5, 5))
            ossPanel.border = javax.swing.border.TitledBorder("OSS Configuration")
            ossPanel.add(javax.swing.JLabel("Endpoint:"))
            ossPanel.add(ossEndpointField)
            ossPanel.add(javax.swing.JLabel("Bucket:"))
            ossPanel.add(ossBucketField)
            ossPanel.add(javax.swing.JLabel("Access Key ID:"))
            ossPanel.add(ossAccessKeyIdField)
            ossPanel.add(javax.swing.JLabel("Access Key Secret:"))
            ossPanel.add(ossAccessKeySecretField)
            add(ossPanel)
            add(javax.swing.Box.createVerticalStrut(10))

            // Options Panel
            val optionsPanel = com.intellij.ui.components.JBPanel<OptionsPanel>(java.awt.GridLayout(0, 2, 5, 5))
            optionsPanel.border = javax.swing.border.TitledBorder("Options")
            optionsPanel.add(autoSyncCheck)
            optionsPanel.add(javax.swing.Box.createGlue())
            optionsPanel.add(javax.swing.JLabel("Sync Interval (min):"))
            optionsPanel.add(syncIntervalSpinner)
            optionsPanel.add(encryptCacheCheck)
            optionsPanel.add(javax.swing.Box.createGlue())
            add(optionsPanel)

            // Test connection button
            val testButton = javax.swing.JButton("Test Connection")
            testButton.addActionListener { testConnection() }
            add(testButton)

            // Provider change listener
            providerCombo.addActionListener {
                updateVisibility()
            }
            updateVisibility()
        }

        private fun updateVisibility() {
            // In a real implementation, this would show/hide panels based on provider
        }

        private fun testConnection() {
            // Implementation would test the connection
            com.intellij.openapi.ui.Messages.showInfoMessage("Connection test would go here", "Test")
        }

        private fun createLabeledComponent(label: String, component: javax.swing.JComponent): javax.swing.JPanel {
            val panel = javax.swing.JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
            panel.add(javax.swing.JLabel(label))
            panel.add(component)
            return panel
        }

        fun isModified(settings: CloudFileSettings): Boolean {
            return providerCombo.selectedItem != settings.state.provider.name ||
                   s3EndpointField.text != settings.state.s3Endpoint ||
                   s3RegionField.text != settings.state.s3Region ||
                   s3BucketField.text != settings.state.s3Bucket ||
                   ossEndpointField.text != settings.state.ossEndpoint ||
                   ossBucketField.text != settings.state.ossBucket ||
                   autoSyncCheck.isSelected != settings.state.autoSync ||
                   syncIntervalSpinner.value != settings.state.syncIntervalMinutes ||
                   encryptCacheCheck.isSelected != settings.state.encryptLocalCache
        }

        fun applyTo(settings: CloudFileSettings) {
            settings.state.provider = CloudFileSettings.StorageProvider.valueOf(providerCombo.selectedItem as String)
            settings.state.s3Endpoint = s3EndpointField.text
            settings.state.s3Region = s3RegionField.text
            settings.state.s3Bucket = s3BucketField.text
            settings.state.ossEndpoint = ossEndpointField.text
            settings.state.ossBucket = ossBucketField.text
            settings.state.autoSync = autoSyncCheck.isSelected
            settings.state.syncIntervalMinutes = syncIntervalSpinner.value as Int
            settings.state.encryptLocalCache = encryptCacheCheck.isSelected

            // Save credentials securely
            val s3AccessKey = String(s3AccessKeyField.password)
            if (s3AccessKey.isNotBlank()) {
                settings.setS3AccessKey(s3AccessKey)
            }
            val s3SecretKey = String(s3SecretKeyField.password)
            if (s3SecretKey.isNotBlank()) {
                settings.setS3SecretKey(s3SecretKey)
            }
            val ossAccessKeyId = String(ossAccessKeyIdField.password)
            if (ossAccessKeyId.isNotBlank()) {
                settings.setOssAccessKeyId(ossAccessKeyId)
            }
            val ossAccessKeySecret = String(ossAccessKeySecretField.password)
            if (ossAccessKeySecret.isNotBlank()) {
                settings.setOssAccessKeySecret(ossAccessKeySecret)
            }
        }

        fun resetFrom(settings: CloudFileSettings) {
            providerCombo.selectedItem = settings.state.provider.name
            s3EndpointField.text = settings.state.s3Endpoint
            s3RegionField.text = settings.state.s3Region
            s3BucketField.text = settings.state.s3Bucket
            ossEndpointField.text = settings.state.ossEndpoint
            ossBucketField.text = settings.state.ossBucket
            autoSyncCheck.isSelected = settings.state.autoSync
            syncIntervalSpinner.value = settings.state.syncIntervalMinutes
            encryptCacheCheck.isSelected = settings.state.encryptLocalCache

            // Load credentials (show as empty for security)
            s3AccessKeyField.text = ""
            s3SecretKeyField.text = ""
            ossAccessKeyIdField.text = ""
            ossAccessKeySecretField.text = ""
        }
    }
}

/**
 * Global rules configuration panel
 */
class GlobalRulesPanel : Configurable {

    private val settings = CloudFileSettings.getInstance()
    private var panel: RulesConfigPanel? = null

    override fun getDisplayName(): String = "Global Rules"

    override fun createComponent(): JComponent {
        panel = RulesConfigPanel("Global")
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        return panel?.isModified(settings.state.globalRules) ?: false
    }

    override fun apply() {
        panel?.applyTo(settings.state.globalRules)
    }

    override fun reset() {
        panel?.resetFrom(settings.state.globalRules)
    }
}

/**
 * Custom rules configuration panel
 */
class CustomRulesPanel : Configurable {

    private val settings = CloudFileSettings.getInstance()
    private var panel: CustomRulesConfigPanel? = null

    override fun getDisplayName(): String = "Custom Rules"

    override fun createComponent(): JComponent {
        panel = CustomRulesConfigPanel()
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        return panel?.isModified(settings.state.customRules) ?: false
    }

    override fun apply() {
        panel?.applyTo(settings.state.customRules)
    }

    override fun reset() {
        panel?.resetFrom(settings.state.customRules)
    }
}

// Type aliases for panel generics
typealias S3Panel = StorageSettingsPanel.StorageConfigPanel
typealias OssPanel = StorageSettingsPanel.StorageConfigPanel
typealias OptionsPanel = StorageSettingsPanel.StorageConfigPanel
