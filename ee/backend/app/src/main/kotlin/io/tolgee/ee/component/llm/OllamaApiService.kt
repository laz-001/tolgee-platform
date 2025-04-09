package io.tolgee.ee.component.llm

import io.tolgee.configuration.tolgee.machineTranslation.LLMProviderInterface
import io.tolgee.dtos.LLMParams
import io.tolgee.service.PromptService
import io.tolgee.util.Logging
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.util.*

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class OllamaApiService(
  private val restTemplate: RestTemplate,
) : Logging {
  fun translate(
    params: LLMParams,
    config: LLMProviderInterface,
  ): PromptService.Companion.PromptResult {
    val headers = HttpHeaders()
    headers.set("content-type", "application/json")

    val messages = mutableListOf<RequestMessage>()

    var promptHasJsonInside = false

    params.messages.forEach {
      if (it.type == LLMParams.Companion.LlmMessageType.TEXT && it.text != null) {
        messages.add(RequestMessage(role = "user", content = it.text))
        promptHasJsonInside = promptHasJsonInside || it.text!!.lowercase().contains("json")
      } else if (it.type == LLMParams.Companion.LlmMessageType.IMAGE && it.image != null) {
        messages.add(
          RequestMessage(
            role = "user",
            images = listOf("${Base64.getEncoder().encode(it.image)}"),
          ),
        )
      }
    }

    val requestBody =
      RequestBody(
        model = config.model!!,
        messages = messages,
        keepAlive = config.keepAlive,
        format = if (promptHasJsonInside && config.format == "json") "json" else null,
      )

    val request = HttpEntity(requestBody, headers)

    val response =
      restTemplate.exchange<ResponseBody>(
        "${config.apiUrl}/api/chat",
        HttpMethod.POST,
        request,
      )

    return PromptService.Companion.PromptResult(
      response.body?.message?.content ?: throw RuntimeException(response.toString()),
      usage = null,
    )
  }

  /**
   * Data structure for mapping the AzureCognitive JSON response objects.
   */
  companion object {
    const val BUCKET_KEY = "tolgee-translate-rate-limit"

    class RequestBody(
      val model: String,
      val messages: List<RequestMessage>,
      val stream: Boolean = false,
      val keepAlive: String? = null,
      val format: String? = "json",
    )

    class RequestMessage(
      val role: String,
      val content: String? = null,
      val images: List<String>? = null,
    )

    class ResponseBody(
      val model: String,
      val message: ResponseMessage,
    )

    class ResponseMessage(
      val role: String,
      val content: String,
    )
  }

  class TooManyRequestsData(
    val error: String? = null,
    val retryAfter: Int? = null,
  )
}
