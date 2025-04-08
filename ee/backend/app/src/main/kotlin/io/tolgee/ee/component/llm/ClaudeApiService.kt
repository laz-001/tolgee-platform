package io.tolgee.ee.component.llm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.tolgee.component.machineTranslation.MtValueProvider
import io.tolgee.configuration.tolgee.machineTranslation.LLMProviderInterface
import io.tolgee.constants.Message
import io.tolgee.dtos.LLMParams
import io.tolgee.dtos.response.prompt.PromptResponseUsageDto
import io.tolgee.exceptions.BadRequestException
import io.tolgee.service.PromptService
import io.tolgee.util.Logging
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.util.*

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class ClaudeApiService(
  private val restTemplate: RestTemplate,
) : Logging {
  fun translate(
    params: LLMParams,
    config: LLMProviderInterface,
  ): PromptService.Companion.PromptResult {
    val headers = HttpHeaders()
    headers.set("content-type", "application/json")
    headers.set("anthropic-version", "2023-06-01")
    headers.set("x-api-key", config.apiKey)

    val messages = mutableListOf<RequestMessage>()

    var promptHasJsonInside = false

    params.messages.forEach {
      if (
        it.type == LLMParams.Companion.LlmMessageType.TEXT &&
        it.text != null
      ) {
        messages.add(RequestMessage(role = "user", content = it.text!!))
        promptHasJsonInside = promptHasJsonInside || it.text!!.lowercase().contains("json")
      } else if (
        it.type == LLMParams.Companion.LlmMessageType.IMAGE &&
        it.image != null
      ) {
        messages.add(
          RequestMessage(
            role = "user",
            content =
              listOf(
                RequestMessageContent(
                  type = "image",
                  source =
                    RequestImage(
                      data = Base64.getEncoder().encodeToString(it.image),
                    ),
                ),
              ),
          ),
        )
      }
    }

    val requestBody =
      RequestBody(
        messages = messages,
        response_format =
          if (promptHasJsonInside) {
            when (config.format) {
              "json_object" -> ResponseFormat(type = "json_object", json_schema = null)
              "json_schema" -> ResponseFormat()
              else -> null
            }
          } else {
            null
          },
        model = config.model,
      )

    val request = HttpEntity(requestBody, headers)

    val response: ResponseEntity<ResponseBody> =
      try {
        restTemplate.exchange<ResponseBody>(
          "${config.apiUrl}/v1/messages",
          HttpMethod.POST,
          request,
        )
      } catch (e: HttpClientErrorException.BadRequest) {
        e.parse()?.get("error")?.get("message")?.toString()?.let {
          throw BadRequestException(Message.LLM_PROVIDER_ERROR, listOf(it))
        }
        throw e
      }

    return PromptService.Companion.PromptResult(
      response.body?.content?.first()?.text ?: throw RuntimeException(response.toString()),
      usage =
        response.body?.usage?.input_tokens?.let {
          PromptResponseUsageDto(
            totalTokens = it.toLong(),
          )
        },
    )
  }

  private fun HttpClientErrorException.parse(): JsonNode? {
    if (!this.responseBodyAsString.isNullOrBlank()) {
      return jacksonObjectMapper().readValue(this.responseBodyAsString)
    }
    return null
  }

  /**
   * Data structure for mapping the AzureCognitive JSON response objects.
   */
  companion object {
    @Suppress("unused")
    class RequestBody(
      val max_tokens: Long = 1000,
      val stream: Boolean = false,
      @JsonInclude(JsonInclude.Include.NON_NULL)
      val response_format: ResponseFormat? = null,
      val messages: List<RequestMessage>,
      val model: String?,
      val temperature: Long? = 0,
    )

    class RequestMessage(
      val role: String,
      val content: Any,
    )

    class RequestMessageContent(
      val type: String,
      val source: RequestImage,
    )

    class RequestImage(
      val type: String = "base64",
      val media_type: String = "image/png",
      val data: String,
    )

    class ResponseFormat(
      val type: String = "json_schema",
      @JsonInclude(JsonInclude.Include.NON_NULL)
      val json_schema: Map<String, Any>? =
        mapOf(
          "name" to "simple_response",
          "schema" to
            mapOf(
              "type" to "object",
              "properties" to
                mapOf(
                  "output" to mapOf("type" to "string"),
                  "contextDescription" to mapOf("type" to "string"),
                ),
              "required" to listOf("output", "contextDescription"),
              "additionalProperties" to false,
            ),
          "strict" to true,
        ),
    )

    class ResponseBody(
      val content: List<ResponseMessage>,
      val usage: ResponseUsage,
    )

    class ResponseMessage(
      val text: String,
    )

    class ResponseUsage(
      val input_tokens: Int,
      val output_tokens: Int,
    )
  }
}
