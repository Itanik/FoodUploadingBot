package ftp.data

import kotlinx.serialization.Serializable

@Serializable
data class Menu(
    val path: String
)