package site.addzero.deploy

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * 单个部署配置
 */
class DeployTarget : BaseState() {
    @get:Tag("name")
    var name by string("")

    // SSH 连接信息
    @get:Tag("host")
    var host by string("")

    @get:Tag("port")
    var port by property(22)

    @get:Tag("username")
    var username by string("")

    @get:Tag("password")
    var password by string("")

    @get:Tag("privateKeyPath")
    var privateKeyPath by string("")

    @get:Tag("passphrase")
    var passphrase by string("")

    @get:Tag("authType")
    var authType by enum(AuthType.PASSWORD)

    @get:Tag("remoteDir")
    var remoteDir by string("/usr/local/app")

    @get:Tag("preDeployCommand")
    var preDeployCommand by string("")

    @get:Tag("postDeployCommand")
    var postDeployCommand by string("")

    @get:Tag("enabled")
    var enabled by property(true)

    fun copyFrom(other: DeployTarget) {
        name = other.name
        host = other.host
        port = other.port
        username = other.username
        password = other.password
        privateKeyPath = other.privateKeyPath
        passphrase = other.passphrase
        authType = other.authType
        remoteDir = other.remoteDir
        preDeployCommand = other.preDeployCommand
        postDeployCommand = other.postDeployCommand
        enabled = other.enabled
    }
}

enum class AuthType {
    PASSWORD,
    KEY_PAIR
}

/**
 * 部署触发器配置
 */
class DeployTrigger : BaseState() {
    @get:Tag("targetName")
    var targetName by string("")
    
    @get:Tag("triggerType")
    var triggerType by enum(TriggerType.MANUAL)
    
    @get:Tag("gitBranch")
    var gitBranch by string("master")
    
    @get:Tag("enabled")
    var enabled by property(true)
}

enum class TriggerType {
    MANUAL,
    GIT_PUSH,
    GIT_COMMIT
}

/**
 * 构建物配置（支持文件或文件夹）
 */
class BuildArtifact : BaseState() {
    @get:Tag("path")
    var path by string("")
    
    @get:Tag("isDirectory")
    var isDirectory by property(false)
    
    @get:Tag("enabled")
    var enabled by property(true)
    
    fun getDisplayName(): String {
        val name = path?.substringAfterLast("/") ?: path ?: ""
        return if (isDirectory) "📁 $name" else "📄 $name"
    }
}

/**
 * 部署配置（目标 + 构建物列表）
 */
class DeployConfiguration : BaseState() {
    @get:Tag("name")
    var name by string("")
    
    @get:Tag("targetName")
    var targetName by string("")
    
    @get:XCollection(style = XCollection.Style.v2)
    val artifacts by list<BuildArtifact>()
    
    @get:Tag("enabled")
    var enabled by property(true)
}

/**
 * 项目级部署配置状态
 */
class JarDeployState : BaseState() {
    @get:XCollection(style = XCollection.Style.v2)
    val targets by list<DeployTarget>()
    
    @get:XCollection(style = XCollection.Style.v2)
    val triggers by list<DeployTrigger>()
    
    @get:XCollection(style = XCollection.Style.v2)
    val configurations by list<DeployConfiguration>()
}
