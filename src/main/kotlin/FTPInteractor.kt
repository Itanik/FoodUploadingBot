import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.files.Document
import ftp.FTPManager
import ftp.FTPManager.Companion.foodImageFileName
import ftp.FTPManager.Companion.foodJsonFileName
import ftp.FTPManager.Companion.foodPath
import ftp.FTPManager.Companion.menuJsonFileName
import ftp.data.Credentials
import ftp.data.Food
import ftp.data.Menu
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.net.ftp.FTPReply
import java.io.File

class FTPInteractor {
    private val scope = MainScope()
    private val credentials = getCredentials()
    private val ftp by lazy { FTPManager(credentials) }

    private fun getCredentials(): Credentials {
        val credJson = FileManager.credentialsFile.bufferedReader().use {
            it.readText()
        }
        return Json.decodeFromString(credJson)
    }

    fun downloadFile(bot: Bot, document: Document, onLoad: (File?) -> Unit) = scope.launch(Default) {
        val bytes = bot.downloadFileBytes(document.fileId)
        if (bytes == null) {
            withContext(Main) { onLoad(null) }
            return@launch
        }

        val file = FileManager.createFile(document.fileName!!).apply { writeBytes(bytes) }
        withContext(Main) { onLoad(file) }
    }

    fun upload(menuFile: File?, tableFile: File?) {

        try {
            val connectionResult = ftp.connect()
            if (!FTPReply.isPositiveCompletion(connectionResult)) throw Exception("Server refuse connection")

            if (menuFile != null) {
                val path = uploadMenuFile(menuFile)
                uploadMenuJson(path)
            }
            if (tableFile != null) {
                uploadTable(tableFile)
                uploadUpdatedFoodFilesJson()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ftp.disconnect()
        }

    }

    private fun uploadUpdatedFoodFilesJson() {
        val foodFiles = ftp.getTableFilesList()
        println("Total number of food files: ${foodFiles.size}")
        val foodData = foodFiles.map {
            Food(it, foodPath.plus(it))
        }
        val format = Json { prettyPrint = true }
        val json = format.encodeToString(foodData)

        if (ftp.uploadFile(foodPath.plus(foodJsonFileName), json.byteInputStream())) {
            println("Successfully uploaded $foodJsonFileName!")
        } else
            throw Exception("Failure! $foodJsonFileName does not uploaded")
    }

    private fun uploadTable(tableFile: File) {
        if (ftp.uploadFile(foodPath.plus(tableFile.name), tableFile.inputStream()))
            println("Successfully uploaded ${tableFile.name}!")
        else
            throw Exception("Failure! ${tableFile.name} does not uploaded")
    }

    private fun uploadMenuJson(path: String) {
        val format = Json { prettyPrint = true }
        val json = format.encodeToString(Menu(path.plus("?").plus(System.currentTimeMillis())))

        if (ftp.uploadFile(foodPath.plus(menuJsonFileName), json.byteInputStream())) {
            println("Successfully uploaded $menuJsonFileName!")
        } else
            throw Exception("Failure! $menuJsonFileName does not uploaded")
    }

    private fun uploadMenuFile(menuFile: File): String {
        val fileName = menuFile.name
        val newName = foodImageFileName.plus(fileName.extension)
        val newPath = foodPath.plus(newName)

        if (ftp.uploadFile(newPath, menuFile.inputStream()))
            println("Successfully uploaded $newName!")
        else
            println("Failure! $newName does not uploaded")
        return newPath
    }

    private val String.extension: String get() = this.split('.').last()
}
