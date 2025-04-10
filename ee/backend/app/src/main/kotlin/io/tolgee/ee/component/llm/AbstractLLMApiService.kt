package io.tolgee.ee.component.llm

import io.tolgee.configuration.tolgee.machineTranslation.LLMProviderInterface
import io.tolgee.dtos.LLMParams
import io.tolgee.service.PromptService
import org.springframework.web.client.RestTemplate

abstract class AbstractLLMApiService {
  abstract fun translate(
    params: LLMParams,
    config: LLMProviderInterface,
    restTemplate: RestTemplate
  ): PromptService.Companion.PromptResult
}
