import data.Credentials
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun main() {
    val credentials = readCredentialsFile()
    val botToken = credentials.token
    val allowedUsers = credentials.allowedUsers
    if (allowedUsers.isEmpty())
        throw Exception("You should provide nicknames of users, what will be able to use this bot")

    println("Суперюзер: ${credentials.allowedUsers.first()}")

    foodBot(
        botToken,
        allowedUsers,
        credentials.menuPage,
        credentials.tablePage,
        WebsiteInteractor(credentials),
    ).run { startPolling() }
}

private fun readCredentialsFile(): Credentials {
    if (!FileManager.credentialsFile.exists())
        throw Exception("You should place credentials.json file in data folder (from where you run it)")
    val credJson = FileManager.credentialsFile.bufferedReader().use {
        it.readText()
    }
    return Json.decodeFromString(credJson)
}
