package io.tolgee.dtos.request.llmProvider

import com.fasterxml.jackson.annotation.JsonSetter
import io.tolgee.model.enums.LLMProviderPriority
import io.tolgee.model.enums.LLMProviderType

data class LLMProviderRequest(
  var name: String,
  var type: LLMProviderType,
  var priority: LLMProviderPriority?,
  var apiKey: String?,
  var apiUrl: String?,
  var model: String?,
  var deployment: String?,
  var keepAlive: String?,
  var format: String?,
) {
  @JsonSetter("type")
  fun setType(type: String) {
    this.type = LLMProviderType.valueOf(type.uppercase())
  }
}
