package ftp.data

import kotlinx.serialization.Serializable

@Serializable
data class Menu(
    val path: String,
    val name: String? = null,
    val lastModificationDate: String? = null
)