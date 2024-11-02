package com.addzero.addl.ai.util.ai.ollama

import com.fasterxml.jackson.annotation.JsonProperty

class OllamaResponse {
    @JsonProperty("model")
     val model: String? = null


    @JsonProperty("created_at")
     val createdAt: String? = null


    @JsonProperty("response")
     val response: String? = null


    @JsonProperty("done")
     val done: Boolean? = null


    @JsonProperty("done_reason")
     val doneReason: String? = null


    @JsonProperty("context")
     val context: List<Int>? = null


    @JsonProperty("total_duration")
     val totalDuration: Int? = null


    @JsonProperty("load_duration")
     val loadDuration: Int? = null


    @JsonProperty("prompt_eval_count")
     val promptEvalCount: Int? = null


    @JsonProperty("prompt_eval_duration")
     val promptEvalDuration: Int? = null


    @JsonProperty("eval_count")
     val evalCount: Int? = null


    @JsonProperty("eval_duration")
     val evalDuration: Int? = null

}