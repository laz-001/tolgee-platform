package io.tolgee.ee.service

import io.tolgee.component.CurrentDateProvider
import io.tolgee.component.machineTranslation.TranslationApiRateLimitException
import io.tolgee.configuration.tolgee.machineTranslation.LLMProperties
import io.tolgee.constants.Caches
import io.tolgee.constants.Message
import io.tolgee.dtos.LLMParams
import io.tolgee.dtos.LLMProviderDto
import io.tolgee.dtos.request.llmProvider.LLMProviderRequest
import io.tolgee.dtos.response.prompt.PromptResponseUsageDto
import io.tolgee.ee.component.llm.ClaudeApiService
import io.tolgee.ee.component.llm.GeminiApiService
import io.tolgee.ee.component.llm.OllamaApiService
import io.tolgee.ee.component.llm.OpenaiApiService
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.model.LLMProvider
import io.tolgee.model.enums.LLMProviderPriority
import io.tolgee.model.enums.LLMProviderType
import io.tolgee.repository.LLMProviderRepository
import io.tolgee.service.PromptService
import io.tolgee.service.organization.OrganizationService
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.set
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException.TooManyRequests
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt

const val TOKEN_PRICE_PER_MILION = 0.000_035 // EUR

@Service
class LLMProviderService(
  private val llmProviderRepository: LLMProviderRepository,
  private val organizationService: OrganizationService,
  private val providerLLMProperties: LLMProperties,
  private val openaiApiService: OpenaiApiService,
  private val ollamaApiService: OllamaApiService,
  private val cacheManager: CacheManager,
  private val currentDateProvider: CurrentDateProvider,
  private val claudeApiService: ClaudeApiService,
  private val geminiApiService: GeminiApiService,
) {
  private val cache: Cache by lazy { cacheManager.getCache(Caches.LLM_PROVIDERS) }
  private var lastUsedMap: MutableMap<String, Long> = mutableMapOf()

  fun getProviderByName(
    organizationId: Long,
    name: String,
    priority: LLMProviderPriority?,
  ): LLMProviderDto {
    val customProviders = getAll(organizationId)
    val serverProviders = getAllServerProviders()
    val providersOfTheName =
      if (customProviders.find { it.name == name } != null) {
        customProviders
      } else {
        serverProviders
      }.filter {
        it.name == name
      }

    val providersWithPriority =
      if (providersOfTheName.find { it.priority == priority } != null) {
        providersOfTheName.filter { it.priority == priority }
      } else {
        providersOfTheName
      }

    if (providersWithPriority.isEmpty()) {
      throw BadRequestException(Message.LLM_PROVIDER_NOT_FOUND, listOf(name))
    }

    val providerInfo = cache.get(name, ProviderInfo::class.java)
    val providers =
      providersWithPriority.filter {
        if (providerInfo != null) {
          providerInfo.suspendMap.getOrDefault(it.id, 0L) < currentDateProvider.date.time
        } else {
          true
        }
      }
    if (providers.isEmpty() && providerInfo?.suspendMap?.isNotEmpty() != null) {
      val closestUnsuspend = providerInfo.suspendMap.map { (_, time) -> time }.min()
      throw TranslationApiRateLimitException(closestUnsuspend)
    }

    var lastUsed = lastUsedMap.get(name)
    val lastUsedIndex = providers.indexOfFirst { it.id == lastUsed }
    val newIndex = (lastUsedIndex + 1) % providers.size
    val provider = providers.get(newIndex)
    lastUsedMap.set(name, provider.id)
    return provider
  }

  fun getAll(organizationId: Long): List<LLMProviderDto> {
    return llmProviderRepository.getAll(organizationId).map { it.toDto() }
  }

  fun callProvider(
    organizationId: Long,
    provider: String,
    params: LLMParams,
    priority: LLMProviderPriority? = null,
  ): PromptService.Companion.PromptResult {
    var lastError: Exception? = null

    // attempt 3 times to find non-rate-limited provider
    for (i in 0..3) {
      val providerConfig = getProviderByName(organizationId, provider, priority)
      try {
        val result =
          when (providerConfig.type) {
            LLMProviderType.OPENAI -> openaiApiService.translate(params, providerConfig)
            LLMProviderType.OLLAMA -> ollamaApiService.translate(params, providerConfig)
            LLMProviderType.CLAUDE -> claudeApiService.translate(params, providerConfig)
            LLMProviderType.GEMINI -> geminiApiService.translate(params, providerConfig)
          }
        result.price = calculatePrice(providerConfig, result.usage)
        return result
      } catch (e: TooManyRequests) {
        suspendProvider(provider, providerConfig.id, 60 * 1000)
        lastError = e
      }
    }
    throw lastError!!
  }

  fun suspendProvider(
    name: String,
    providerId: Long,
    period: Long,
  ) {
    val providerInfo = cache.get(name, ProviderInfo::class.java) ?: ProviderInfo()
    providerInfo.suspendMap.set(providerId, currentDateProvider.date.time + period)
    cache.set(name, providerInfo)
  }

  fun createProvider(
    organizationId: Long,
    dto: LLMProviderRequest,
  ): LLMProviderDto {
    val provider =
      LLMProvider(
        name = dto.name,
        type = dto.type,
        priority = dto.priority,
        apiKey = dto.apiKey,
        apiUrl = dto.apiUrl,
        model = dto.model,
        deployment = dto.deployment,
        keepAlive = dto.keepAlive,
        format = dto.format,
        organization = organizationService.get(organizationId),
        pricePerMillionInput = null,
        pricePerMillionOutput = null,
      )
    llmProviderRepository.save(provider)
    return provider.toDto()
  }

  fun updateProvider(
    organizationId: Long,
    providerId: Long,
    dto: LLMProviderRequest,
  ): LLMProviderDto {
    val provider = llmProviderRepository.findById(providerId).getOrNull() ?: throw NotFoundException()
    provider.name = dto.name
    provider.type = dto.type
    provider.priority = dto.priority
    provider.apiKey = dto.apiKey
    provider.apiUrl = dto.apiUrl
    provider.model = dto.model
    provider.deployment = dto.deployment
    provider.keepAlive = dto.keepAlive
    provider.format = dto.format
    provider.organization = organizationService.get(organizationId)
    llmProviderRepository.save(provider)
    return provider.toDto()
  }

  fun deleteProvider(
    organizationId: Long,
    providerId: Long,
  ) {
    llmProviderRepository.deleteById(providerId)
  }

  fun getAllServerProviders(): List<LLMProviderDto> {
    return providerLLMProperties.providers.mapIndexed { index, llmProvider ->
      // server configured providers are indexed like -1, -2, -3, to identify them
      llmProvider.toDto(-(index.toLong()) - 1)
    }
  }

  fun calculatePrice(
    providerConfig: LLMProviderDto,
    usage: PromptResponseUsageDto?,
  ): Int {
    val pricePerMillionInput: Double = providerConfig.pricePerMillionInput ?: 0.0
    val pricePerMillionOutput: Double = providerConfig.pricePerMillionOutput ?: 0.0
    val inputTokens: Long = usage?.inputTokens ?: 0L
    val outputTokens: Long = usage?.outputTokens ?: 0L
    val cachedTokens: Long = usage?.cachedTokens ?: 0L

    return (
      (
        (
          ((inputTokens - cachedTokens) * pricePerMillionInput) +
            (outputTokens * pricePerMillionOutput)
        )
      ) * TOKEN_PRICE_PER_MILION * 100
    ).roundToInt()
  }

  companion object {
    data class ProviderInfo(
      var suspendMap: MutableMap<Long, Long> = mutableMapOf(),
    )
  }
}
