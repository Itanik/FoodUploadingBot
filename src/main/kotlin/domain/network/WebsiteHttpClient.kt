package domain.network

import data.Credentials
import data.Food
import data.Menu
import misc.foodJsonFileName
import misc.foodPath
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import misc.menuJsonFileName
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebsiteHttpClient(private val credentials: Credentials) {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun getMenu(): Menu {
        val menuJson = callForJson("http://${credentials.host}$foodPath$menuJsonFileName")
        return Json.decodeFromString(menuJson)
    }

    suspend fun getTable(): List<Food> {
        val foodListJson = callForJson("http://${credentials.host}$foodPath$foodJsonFileName")
        return Json.decodeFromString(foodListJson)
    }

    private suspend fun callForJson(url: String) = suspendCoroutine<String> { continuation ->
        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).execute().use { response ->
            response.body()?.string()?.let { json ->
                continuation.resume(json)
                return@suspendCoroutine
            }
            continuation.resumeWithException(Exception("Cannot resolve response body"))
        }
    }
}