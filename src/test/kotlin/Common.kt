import data.Credentials
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun readCredentialsFile(): Credentials {
    if (!FileManager.credentialsFile.exists())
        throw Exception("You should place credentials.json file in data folder (from where you run it)")
    val credJson = FileManager.credentialsFile.bufferedReader().use {
        it.readText()
    }
    return Json.decodeFromString(credJson)
}