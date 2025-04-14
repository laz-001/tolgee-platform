package io.tolgee.model

import io.tolgee.model.key.Key
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne

@Entity
class AiPlaygroundResult(
  @ManyToOne
  var project: Project,

  @ManyToOne
  var key: Key,

  @ManyToOne
  var language: Language,

  @ManyToOne
  var user: UserAccount,

  var translation: String? = null,

  var contextDescription: String? = null
) : StandardAuditModel()
