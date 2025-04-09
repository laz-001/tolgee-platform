package io.tolgee.ee.service.prompt

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.HandlebarsException
import io.tolgee.component.fileStorage.FileStorage
import io.tolgee.component.machineTranslation.MtValueProvider
import io.tolgee.constants.Message
import io.tolgee.dtos.LLMParams
import io.tolgee.dtos.request.prompt.PromptDto
import io.tolgee.dtos.request.prompt.PromptRunDto
import io.tolgee.ee.data.prompt.PromptVariableDto
import io.tolgee.ee.service.LLMProviderService
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.model.Prompt
import io.tolgee.model.enums.LLMProviderPriority
import io.tolgee.model.key.Key
import io.tolgee.repository.PromptRepository
import io.tolgee.service.PromptService
import io.tolgee.service.key.KeyService
import io.tolgee.service.key.ScreenshotService
import io.tolgee.service.language.LanguageService
import io.tolgee.service.machineTranslation.MetadataKey
import io.tolgee.service.machineTranslation.MetadataProvider
import io.tolgee.service.machineTranslation.MtTranslatorContext
import io.tolgee.service.machineTranslation.PluralTranslationUtil
import io.tolgee.service.project.ProjectService
import io.tolgee.service.security.SecurityService
import io.tolgee.service.translation.TranslationService
import io.tolgee.util.ImageConverter
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.ByteArrayInputStream
import kotlin.jvm.optionals.getOrNull

@Primary
@Service
class PromptServiceEeImpl(
  private val securityService: SecurityService,
  private val keyService: KeyService,
  private val languageService: LanguageService,
  private val projectService: ProjectService,
  private val translationService: TranslationService,
  private val fileStorage: FileStorage,
  private val screenshotService: ScreenshotService,
  private val applicationContext: ApplicationContext,
  private val promptRepository: PromptRepository,
  private val providerService: LLMProviderService,
  private val promptFragmentsService: PromptFragmentsService,
  private val promptDefaultService: PromptDefaultService,
) : PromptService {
  fun getAllPaged(
    projectId: Long,
    pageable: Pageable,
    search: String?,
  ): Page<Prompt> {
    return promptRepository.getAllPaged(projectId, pageable, search)
  }

  fun createPrompt(
    projectId: Long,
    dto: PromptDto,
  ): Prompt {
    val prompt =
      Prompt(
        name = dto.name,
        template = dto.template,
        project = projectService.get(projectId),
        providerName = dto.providerName,
      )
    promptRepository.save(prompt)
    return prompt
  }

  override fun findPromptOrDefaultDto(
    projectId: Long,
    promptId: Long?,
  ): PromptDto {
    if (promptId != null) {
      val prompt = promptRepository.findPrompt(projectId, promptId) ?: throw NotFoundException(Message.PROMPT_NOT_FOUND)
      return PromptDto(prompt.name, template = prompt.template, providerName = prompt.providerName)
    } else {
      return getDefaultPrompt()
    }
  }

  override fun findPrompt(
    projectId: Long,
    promptId: Long,
  ): Prompt {
    return promptRepository.findPrompt(projectId, promptId) ?: throw NotFoundException(Message.PROMPT_NOT_FOUND)
  }

  fun updatePrompt(
    projectId: Long,
    promptId: Long,
    dto: PromptDto,
  ): Prompt {
    val prompt = findPrompt(projectId, promptId)
    prompt.name = dto.name
    prompt.template = dto.template
    prompt.providerName = dto.providerName
    promptRepository.save(prompt)
    return prompt
  }

  fun deletePrompt(
    projectId: Long,
    promptId: Long,
  ) {
    val prompt = this.findPrompt(projectId, promptId)
    promptRepository.delete(prompt)
  }

  fun encodeScreenshot(
    id: Long,
    type: String,
  ): String {
    return "[[screenshot_${type}_$id]]"
  }

  fun encodeScreenshots(
    list: List<String>,
    type: String,
  ): String {
    return list.map { id -> encodeScreenshot(id.toLong(), type) }.joinToString("\n")
  }

  @Transactional
  fun getVariables(
    projectId: Long,
    keyId: Long?,
    targetLanguageId: Long?,
  ): MutableList<Variable> {
    var key: Key? = null
    if (keyId !== null) {
      key = keyService.find(keyId) ?: throw NotFoundException(Message.KEY_NOT_FOUND)
      keyService.checkInProject(key, projectId)
    }

    val project = projectService.get(projectId)

    val languages = languageService.getProjectLanguages(projectId)

    val tLanguage =
      targetLanguageId?.let {
        languageService.find(targetLanguageId, projectId) ?: throw NotFoundException(Message.LANGUAGE_NOT_FOUND)
      }
    val sLanguage = languageService.get(project.baseLanguage!!.id, projectId)

    val sTranslation = key?.let { translationService.find(it, sLanguage).getOrNull() }
    val tTranslation = key?.let { tLanguage?.let { translationService.find(key, tLanguage).getOrNull() } }

    val variables = mutableListOf<Variable>()

    val source = Variable("source")

    source.props.add(Variable("language", sLanguage.name))
    source.props.add(Variable("translation", sTranslation?.text ?: ""))
    source.props.add(Variable("languageNote", sLanguage.aiTranslatorPromptDescription ?: ""))
    variables.add(source)

    val target = Variable("target")

    target.props.add(Variable("language", tLanguage?.name))
    target.props.add(Variable("translation", tTranslation?.text ?: ""))

    val context = MtTranslatorContext(projectId, applicationContext, false)
    val pluralFormsWithReplacedParam =
      if (key != null && key.isPlural && sTranslation != null && tLanguage?.tag != null) {
        context.getPluralFormsReplacingReplaceParam(
          sTranslation.text ?: "",
        )
      } else {
        null
      }

    val pluralSourceExamples =
      pluralFormsWithReplacedParam?.let {
        PluralTranslationUtil.getSourceExamples(
          context.baseLanguage.tag,
          tLanguage!!.tag,
          it,
        )
      }

    target.props.add(
      Variable(
        "pluralFormExamples",
        value = pluralSourceExamples?.map { "${it.key} (e.g. ${it.value})" }?.joinToString("\n"),
      ),
    )

    target.props.add(
      Variable(
        "exactForms",
        value = pluralSourceExamples?.map { it.key }?.joinToString(" "),
      ),
    )

    target.props.add(
      Variable(
        "exampleIcuPlural",
        value = pluralSourceExamples?.let { "{count, plural, ${it.map { "${it.key} {...}" }.joinToString(" ")}}" },
      ),
    )
    target.props.add(
      Variable(
        "languageNote",
        tLanguage?.aiTranslatorPromptDescription ?: "",
      ),
    )

    variables.add(target)

    val otherVar = Variable("other")

    languages.filter {
      it.id != sLanguage.id && it.id != tLanguage?.id
    }.forEach { language ->
      val langVar = Variable(language.tag)
      langVar.props.add(Variable("language", language.name))
      langVar.props.add(Variable("languageNote", language.aiTranslatorPromptDescription))
      langVar.props.add(
        Variable("translation", lazyValue = {
          key?.let { translationService.find(it, language).getOrNull()?.text }
        }),
      )
      otherVar.props.add(langVar)
    }

    variables.add(otherVar)

    val projectVar = Variable("project")

    projectVar.props.add(Variable("name", project.name))
    projectVar.props.add(Variable("description", project.aiTranslatorPromptDescription ?: ""))

    variables.add(projectVar)

    val keyVar = Variable("key")
    keyVar.props.add(Variable("name", key?.name))
    keyVar.props.add(Variable("description", key?.keyMeta?.description ?: ""))
    variables.add(keyVar)

    val relatedKeys = Variable("relatedKeys")
    relatedKeys.props.add(
      Variable(
        "json",
        description = "Related keys in json format (based on context extraction)",
        lazyValue = {
          val context = MtTranslatorContext(projectId, applicationContext, false)
          val metadataProvider = MetadataProvider(context)
          val closeItems =
            tLanguage?.let {
              key?.let {
                metadataProvider.getCloseItems(
                  sLanguage,
                  tLanguage,
                  MetadataKey(key.id, sTranslation?.text ?: "", tLanguage.id),
                )
              }
            }
          if (!closeItems.isNullOrEmpty()) {
            closeItems.joinToString("\n") {
              val mapper = ObjectMapper()
              mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
              mapper.writeValueAsString(it)
            }
          } else {
            null
          }
        },
      ),
    )
    variables.add(relatedKeys)

    val translationMemory = Variable("translationMemory")
    translationMemory.props.add(
      Variable(
        "json",
        description = "",
        lazyValue = {
          val context = MtTranslatorContext(projectId, applicationContext, false)
          val metadataProvider = MetadataProvider(context)
          val closeItems =
            tLanguage?.let {
              key?.let {
                metadataProvider.getExamples(
                  tLanguage,
                  isPlural = key.isPlural,
                  text = sTranslation?.text ?: "",
                  keyId = key.id,
                )
              }
            }
          if (!closeItems.isNullOrEmpty()) {
            closeItems.joinToString("\n") {
              val mapper = ObjectMapper()
              mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
              mapper.writeValueAsString(it)
            }
          } else {
            null
          }
        },
      ),
    )
    variables.add(translationMemory)

    val screenshots = key?.keyScreenshotReferences?.map { "${it.screenshot.id}" }
    val screenshotsVar = Variable("screenshots")

    screenshotsVar.props.add(
      Variable(
        "first",
        screenshots?.let { encodeScreenshots(it.take(1), "small") },
      ),
    )

    screenshotsVar.props.add(
      Variable(
        "firstFull",
        screenshots?.let { encodeScreenshots(it.take(1), "full") },
      ),
    )

    screenshotsVar.props.add(
      Variable(
        "all",
        screenshots?.let { encodeScreenshots(it, "small") },
      ),
    )

    screenshotsVar.props.add(
      Variable(
        "allFull",
        screenshots?.let { encodeScreenshots(it, "full") },
      ),
    )

    variables.add(screenshotsVar)

    val fragments = Variable("fragment", props = promptFragmentsService.getAllFragments())
    variables.add(fragments)

    return variables
  }

  fun createVariablesLazyMap(params: List<Variable>): LazyMap {
    val mapParams =
      params.map {
        it.name to it
      }.toMap()
    val lazyMap = LazyMap()
    lazyMap.setMap(mapParams)
    return lazyMap
  }

  @Transactional
  fun getPrompt(
    projectId: Long,
    data: PromptRunDto,
  ): String {
    try {
      val params = getVariables(projectId, data.keyId, data.targetLanguageId)

      val handlebars = Handlebars()

      val paramsForFragments = createVariablesLazyMap(params)

      val fragments = params.find { it.name == "fragment" }?.props

      fragments?.forEach {
        val template = handlebars.compileInline(it.value)
        it.value = template.apply(paramsForFragments)
      }

      val finalParams = createVariablesLazyMap(params)

      val template = handlebars.compileInline(data.template)
      val prompt = template.apply(finalParams)
      // remove excessive newlines and trim
      return prompt.replace(Regex("\n(\\s*\n)+"), "\n\n").trim()
    } catch (e: HandlebarsException) {
      throw BadRequestException(
        Message.LLM_TEMPLATE_PARSING_ERROR,
        listOf(e.error.reason, e.error.line, e.error.column),
      )
    }
  }

  @Transactional
  fun getLlmMessages(
    prompt: String,
    keyId: Long,
  ): List<LLMParams.Companion.LlmMessage> {
    val key = keyService.find(keyId) ?: throw NotFoundException(Message.KEY_NOT_FOUND)

    val pattern = Regex("\\[\\[screenshot_(full|small)_(\\d+)]]")

    val parts = pattern.splitWithMatches(prompt)
    return parts.mapNotNull {
      if (pattern.matches(it)) {
        val match = pattern.matchEntire(it) ?: throw Error()
        // Extract size and id from the match groups
        val size = match.groups[1]!!.value // full or small
        val id = match.groups[2]!!.value.toLong() // number
        val screenshot = key.keyScreenshotReferences.find { it.screenshot.id == id }?.screenshot
        if (screenshot == null) {
          null
        } else {
          val file =
            if (size === "full") {
              screenshot.filename
            } else {
              screenshot.middleSizedFilename ?: screenshot.filename
            }

          var image =
            fileStorage.readFile(
              screenshotService.getScreenshotPath(file),
            )

          if (screenshot.keyScreenshotReferences.find { it.key.id == key.id } !== null) {
            val converter =
              ImageConverter(
                ByteArrayInputStream(
                  fileStorage.readFile(
                    screenshotService.getScreenshotPath(file),
                  ),
                ),
              )
            image = converter.highlightKeys(screenshot, listOf(key.id)).toByteArray()
          }

          LLMParams.Companion.LlmMessage(
            type = LLMParams.Companion.LlmMessageType.IMAGE,
            image = image,
          )
        }
      } else {
        LLMParams.Companion.LlmMessage(
          type = LLMParams.Companion.LlmMessageType.TEXT,
          text = it,
        )
      }
    }
  }

  // Helper function to split and keep matches
  fun Regex.splitWithMatches(input: String): List<String> {
    val result = mutableListOf<String>()
    var lastIndex = 0

    this.findAll(input).forEach { match ->
      // Add text before the match if exists
      if (match.range.first > lastIndex) {
        result.add(input.substring(lastIndex, match.range.first))
      }
      // Add the match itself
      result.add(match.value)
      lastIndex = match.range.last + 1
    }

    // Add remaining text after last match
    if (lastIndex < input.length) {
      result.add(input.substring(lastIndex))
    }

    return result
  }

  fun runPrompt(
    organizationId: Long,
    params: LLMParams,
    provider: String,
    priority: LLMProviderPriority?,
  ): PromptService.Companion.PromptResult {
    val result =
      try {
        providerService.callProvider(organizationId, provider, params, priority)
      } catch (e: ResourceAccessException) {
        throw BadRequestException(Message.LLM_PROVIDER_ERROR, listOf(e.message))
      } catch (e: HttpClientErrorException) {
        throw BadRequestException(Message.LLM_PROVIDER_ERROR, listOf(e.message))
      }

    result.parsedJson =
      try {
        jacksonObjectMapper().readValue<JsonNode>(result.response)
      } catch (e: JsonProcessingException) {
        null
      }

    return result
  }

  fun getTranslationFromPromptResult(result: PromptService.Companion.PromptResult): MtValueProvider.MtResult {
    val json = result.parsedJson ?: throw BadRequestException(Message.LLM_PROVIDER_NOT_RETURNED_JSON)
    val translation = json.get("output").asText() ?: throw BadRequestException(Message.LLM_PROVIDER_NOT_RETURNED_JSON)
    return MtValueProvider.MtResult(
      translation,
      contextDescription = json.get("contextDescription").asText(),
      price = 0,
      usage = result.usage,
    )
  }

  @Transactional
  fun translateViaPrompt(
    projectId: Long,
    data: PromptRunDto,
    priority: LLMProviderPriority?,
  ): MtValueProvider.MtResult {
    val project = projectService.get(projectId)
    val prompt = getPrompt(projectId, data)
    val messages = getLlmMessages(prompt, data.keyId)
    val result = runPrompt(project.organizationOwner.id, LLMParams(messages), data.provider, priority)
    return getTranslationFromPromptResult(result)
  }

  override fun translateAndUpdateTranslation(
    projectId: Long,
    data: PromptRunDto,
    priority: LLMProviderPriority?,
  ) {
    val result = translateViaPrompt(projectId, data, priority)
    val translation = translationService.getOrCreate(data.keyId, data.targetLanguageId)
    translation.text = result.translated
    translationService.save(translation)
  }

  fun getDefaultPrompt(): PromptDto {
    return promptDefaultService.getDefaultPrompt()
  }

  companion object {
    class LazyMap : AbstractMap<String, Any?>() {
      private lateinit var internalMap: Map<String, Variable>

      fun setMap(map: Map<String, Variable>) {
        internalMap = map
      }

      override fun get(key: String): Any? {
        val promptValue = internalMap.get(key)

        if (!promptValue?.props.isNullOrEmpty()) {
          val mapParams =
            promptValue!!.props.map {
              it.name to it
            }.toMap()

          val lazyMap = LazyMap()
          lazyMap.setMap(mapParams)
          return lazyMap
        }

        val stringValue = promptValue?.lazyValue?.let { it() } ?: promptValue?.value
        return stringValue?.let { Handlebars.SafeString(it) }
      }

      override val entries: Set<Map.Entry<String, Any?>>
        get() {
          return internalMap.entries.map { (key) -> Entry(key) { get(key) } }.toSet()
        }
    }

    private class Entry(override val key: String, val valGetter: () -> Any?) : Map.Entry<String, Any?> {
      override val value: Any?
        get() = valGetter()
    }

    class Variable(
      val name: String,
      var value: String? = null,
      var lazyValue: (() -> String?)? = null,
      val description: String? = null,
      val props: MutableList<Variable> = mutableListOf(),
    ) {
      fun toPromptVariableDto(): PromptVariableDto {
        return PromptVariableDto(
          name = name,
          description = description,
          value = value,
          props =
            if (props.size != 0) {
              props.map { it.toPromptVariableDto() }.toMutableList()
            } else {
              null
            },
        )
      }
    }
  }
}
