package io.tolgee.dtos.response.prompt

data class PromptResponseDto(
  val prompt: String,
  val result: String,
  val price: Int?,
  val usage: PromptResponseUsageDto?,
)
