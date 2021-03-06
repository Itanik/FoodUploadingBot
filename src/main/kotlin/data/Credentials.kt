package data

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(
    val token: String,
    val allowedUsers: List<String>,
    val host: String,
    val user: String,
    val password: String,
    val menuPage: String,
    val tablePage: String
)