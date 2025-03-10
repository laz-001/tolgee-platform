package io.tolgee.service

import io.tolgee.dtos.request.prompt.PromptDto
import io.tolgee.dtos.request.prompt.PromptRunDto
import io.tolgee.model.Prompt
import io.tolgee.model.enums.LLMProviderPriority

interface PromptService {
  fun translateAndUpdateTranslation(
    projectId: Long,
    data: PromptRunDto,
    priority: LLMProviderPriority?,
  )

  fun findPromptOrDefaultDto(
    projectId: Long,
    promptId: Long? = null,
  ): PromptDto

  fun findPrompt(
    projectId: Long,
    promptId: Long,
  ): Prompt
}
