package io.tolgee.ee.service.prompt

import io.tolgee.dtos.request.prompt.PromptDto
import org.springframework.stereotype.Service

@Service
class PromptDefaultService {
  fun getDefaultPrompt(): PromptDto {
    return PromptDto(
      name = "default",
      template =
        """
        {{fragment.intro}}

        {{fragment.styleInfo}}

        {{fragment.projectDescription}}
        
        {{fragment.languageNotes}}

        {{fragment.icuInfo}}

        {{fragment.screenshots}}

        {{fragment.relatedKeys}}

        {{fragment.translationMemory}}

        {{fragment.keyInfo}}

        {{fragment.translationInfo}}

        {{fragment.translateJson}}
        """.trimIndent(),
      providerName = "default",
    )
  }
}
