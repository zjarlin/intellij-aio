package site.addzero.gradle.buddy.intentions.completion

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Gradle 依赖推荐服务
 *
 * 提供热门依赖推荐列表，用于自动补全
 */
@Service(Service.Level.PROJECT)
@State(
    name = "GradleBuddyCompletionSettings",
    storages = [Storage("gradleBuddyCompletionSettings.xml")]
)
class GradleDependencySuggestionService : PersistentStateComponent<GradleDependencySuggestionService.State> {

    data class State(
        var customSuggestions: MutableList<String> = defaultSuggestions.toMutableList()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    // 获取所有建议
    fun getAllSuggestions(): List<String> = myState.customSuggestions.toList()

    // 添加自定义建议
    fun addCustomSuggestion(suggestion: String) {
        if (suggestion !in myState.customSuggestions) {
            myState.customSuggestions.add(suggestion)
        }
    }

    companion object {
        val defaultSuggestions = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "com.google.guava:guava",
            "com.squareup.okhttp3:okhttp",
            "com.squareup.retrofit2:retrofit",
            "com.fasterxml.jackson.core:jackson-databind",
            "com.fasterxml.jackson.module:jackson-module-kotlin",
            "org.apache.commons:commons-lang3",
            "org.slf4j:slf4j-api",
            "ch.qos.logback:logback-classic",
            "junit:junit",
            "org.junit.jupiter:junit-jupiter",
            "org.mockito:mockito-core",
            "org.springframework.boot:spring-boot-starter-web",
            "org.springframework.boot:spring-boot-starter-data-jpa",
            "org.springframework.boot:spring-boot-starter-test",
            "com.google.dagger:dagger",
            "androidx.compose.ui:ui",
            "androidx.compose.runtime:runtime",
            "androidx.compose.material:material",
            "io.insert-koin:koin-core",
            "io.insert-koin:koin-android",
            "org.jetbrains.kotlin.plugin.serialization:kotlin-serialization",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm",
            "com.google.code.gson:gson",
            "com.squareup.moshi:moshi",
            "com.squareup.moshi:moshi-kotlin",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-xml",
            "com.fasterxml.jackson.datatype:jackson-datatype-jdk8",
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310",
            "org.hibernate.orm:hibernate-core",
            "org.hibernate.validator:hibernate-validator",
            "org.springframework.data:spring-data-jpa",
            "org.springframework.security:spring-security-web",
            "org.springframework.security:spring-security-config",
            "org.springframework.boot:spring-boot-starter-security",
            "org.springframework.boot:spring-boot-starter-actuator",
            "org.springframework.boot:spring-boot-starter-validation",
            "com.baomidou:mybatis-plus-boot-starter",
            "com.baomidou:mybatis-plus",
            "com.alibaba:druid-spring-boot-starter",
            "com.alibaba:druid",
            "com.alibaba.fastjson2:fastjson2",
            "com.alibaba.fastjson2:fastjson2-kotlin",
            "org.projectlombok:lombok",
            "org.mapstruct:mapstruct",
            "org.mybatis:mybatis",
            "org.mybatis:mybatis-spring",
            "org.mybatis:mybatis-spring-boot-starter",
            "com.baomidou.mybatisplus:mybatis-plus-extension",
            "com.google.zxing:core",
            "com.google.zxing:javase",
            "cn.hutool:hutool-all",
            "com.google.code.gson:gson",
            "com.fasterxml.jackson.core:jackson-annotations",
            "com.fasterxml.jackson.core:jackson-databind",
            "com.fasterxml.jackson.module:jackson-module-kotlin",
            "com.fasterxml.jackson.datatype:jackson-datatype-jdk8",
            "org.apache.commons:commons-lang3",
            "org.apache.commons:commons-collections4",
            "org.apache.commons:commons-io",
            "org.assertj:assertj-core",
            "org.testng:testng",
            "org.jetbrains.kotlin:kotlin-test",
            "com.intellij:annotations",
            "org.jetbrains:annotations"
        )

        fun getInstance(project: Project): GradleDependencySuggestionService = project.service()
    }
}
