package io.tolgee.model

import io.tolgee.configuration.tolgee.machineTranslation.LLMProviderInterface
import io.tolgee.dtos.LLMProviderDto
import io.tolgee.model.enums.LLMProviderPriority
import io.tolgee.model.enums.LLMProviderType
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
class LLMProvider(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0L,
  @field:NotBlank
  override var name: String = "",
  @field:Enumerated(EnumType.STRING)
  override var type: LLMProviderType,
  @field:Enumerated(EnumType.STRING)
  override var priority: LLMProviderPriority?,
  override var apiKey: String?,
  override var apiUrl: String?,
  override var model: String?,
  override var deployment: String?,
  override var keepAlive: String?,
  override var format: String?,
  override var pricePerMillionInput: Long?,
  override var pricePerMillionOutput: Long?,
  @ManyToOne
  @JoinColumn(name = "organization_id")
  var organization: Organization,
) : AuditModel(), LLMProviderInterface {
  fun toDto(): LLMProviderDto {
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
