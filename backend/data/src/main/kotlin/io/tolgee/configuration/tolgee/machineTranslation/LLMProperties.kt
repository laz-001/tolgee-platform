package io.tolgee.configuration.tolgee.machineTranslation

import io.tolgee.configuration.annotations.DocProperty
import io.tolgee.dtos.LLMProviderDto
import io.tolgee.model.enums.LLMProviderPriority
import io.tolgee.model.enums.LLMProviderType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tolgee.llm")
class LLMProperties {
  var enabled: Boolean = false
  var providers: MutableList<LLMProvider> = mutableListOf()

  open class LLMProvider(
    @DocProperty("User visible provider name")
    override var name: String = "default",
    @DocProperty("Provider type (OPENAI or OLLAMA)")
    override var type: LLMProviderType,
    override var priority: LLMProviderPriority?,
    override var apiKey: String?,
    override var apiUrl: String?,
    override var model: String?,
    override var deployment: String?,
    override var keepAlive: String?,
    override var format: String?,
    override var pricePerMillionInput: Double?,
    override var pricePerMillionOutput: Double?,
  ) : LLMProviderInterface {
    fun toDto(id: Long): LLMProviderDto {
      return LLMProviderDto(
        id = id,
        name = name,
        type = type,
        priority = priority,
        apiKey = apiKey,
        apiUrl = apiUrl,
        model = model,
        deployment = deployment,
        keepAlive = keepAlive,
        format = format,
        pricePerMillionInput = pricePerMillionInput,
        pricePerMillionOutput = pricePerMillionOutput,
      )
    }
  }
}
