package eu.kanade.tachiyomi.extension.all.mangadexcn.dto

import eu.kanade.tachiyomi.extension.all.mangadexcn.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.USER)
data class UserDto(override val attributes: UserAttributes? = null) : EntityDto()

@Serializable
data class UserAttributes(val username: String) : AttributesDto()
