import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.vibetask"
//        name = "Vibe Task"
        version = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
}

dependencies {
    // 无外部依赖，使用平台自带功能
}
