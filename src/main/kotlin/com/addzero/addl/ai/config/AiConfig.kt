package com.addzero.addl.ai.config

import org.springframework.ai.ollama.OllamaEmbeddingModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class AiConfig {
    @Value("\${spring.ai.ollama.base-url}")
    lateinit var ollamaUrl: String



    @Bean
    open//    @ConditionalOnProperty("spring.ai.ollama")
    fun embeddingModel(): OllamaEmbeddingModel {
        return OllamaEmbeddingModel(OllamaApi(ollamaUrl))


    }
}