package eu.kanade.tachiyomi.extension.all.mangadexcn.dto

import eu.kanade.tachiyomi.extension.all.mangadexcn.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.AUTHOR)
data class AuthorDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
@SerialName(MDConstants.ARTIST)
data class ArtistDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
data class AuthorArtistAttributesDto(val name: String) : AttributesDto()
