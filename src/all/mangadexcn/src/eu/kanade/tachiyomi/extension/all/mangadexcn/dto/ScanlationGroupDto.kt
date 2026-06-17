package eu.kanade.tachiyomi.extension.all.mangadexcn.dto

import eu.kanade.tachiyomi.extension.all.mangadexcn.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.SCANLATION_GROUP)
data class ScanlationGroupDto(override val attributes: ScanlationGroupAttributes? = null) : EntityDto()

@Serializable
data class ScanlationGroupAttributes(val name: String) : AttributesDto()
