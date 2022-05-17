import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.files.Document
import data.Credentials
import data.Food
import data.Menu
import ftp.FTPManager
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class ProcessingResult {
    data class Success(val data: String) : ProcessingResult()
    object InProgress : ProcessingResult()
    object ErrorWrongDocumentType : ProcessingResult()
    data class Error(val message: String) : ProcessingResult()
}

/**
 *  Выполняет функции общения с сайтом, адрес которого указан в credentials.json
 */
class WebsiteInteractor {
    private val scope = MainScope()
    private val credentials = getCredentials()
    private val ftp by lazy { FTPManager(credentials) }
    private val timeFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
    }
    private val websiteHttpClient by lazy { WebsiteHttpClient(credentials) }

    private fun getCredentials(): Credentials {
        val credJson = FileManager.credentialsFile.bufferedReader().use {
            it.readText()
        }
        return Json.decodeFromString(credJson)
    }

    fun processFile(bot: Bot, document: Document, onResult: (ProcessingResult) -> Unit) = scope.launch(Default) {
        val extension = document.fileName?.split('.')?.last() ?: ""
        when (extension) {
            "jpg", "jpeg", "png", "pdf" -> {
                onResult(ProcessingResult.InProgress)
                val file = downloadFile(bot, document)
                if (file == null) {
                    onResult(ProcessingResult.Error("Ошибка при скачивании файла"))
                    return@launch
                }
                uploadMenu(file)
                onResult(ProcessingResult.Success("Меню успешно загружено"))
            }
            "xlsx" -> {
                onResult(ProcessingResult.InProgress)
                val file = downloadFile(bot, document)
                if (file == null) {
                    onResult(ProcessingResult.Error("Ошибка при скачивании файла"))
                    return@launch
                }
                uploadTable(file)
                // TODO: Сделать обработку ошибок здесь, иначе в любых случаях будет сообщено об успешной попытке
                onResult(ProcessingResult.Success("Таблица успешно загружена"))
            }
            else -> {
                onResult(ProcessingResult.ErrorWrongDocumentType)
            }
        }
    }

    fun getLastAddedMenu(onResult: (Menu?) -> Unit) = scope.launch(Default) {
        try {
            onResult(websiteHttpClient.getMenu())
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(null)
        }
    }

    fun getLastAddedFoodTable(onResult: (Food?) -> Unit) = scope.launch(Default) {
        try {
            val foodList = websiteHttpClient.getTable().sortedBy { it.name }
            onResult(foodList.last())
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(null)
        }
    }

    private fun downloadFile(bot: Bot, document: Document): File? {
        val bytes = bot.downloadFileBytes(document.fileId) ?: return null
        return FileManager.createFile(document.fileName!!).apply { writeBytes(bytes) }
    }

    private fun uploadMenu(menuFile: File) {
        try {
            val connectionResult = ftp.connect()
            if (!FTPReply.isPositiveCompletion(connectionResult)) throw Exception("Server refuse connection")
            val path = uploadMenuFile(menuFile)
            uploadMenuJson(path, menuFile.name)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ftp.disconnect()
        }

    }

    private fun uploadTable(tableFile: File) {
        try {
            val connectionResult = ftp.connect()
            if (!FTPReply.isPositiveCompletion(connectionResult)) throw Exception("Server refuse connection")
            if (ftp.uploadFile(foodPath.plus(tableFile.name), tableFile.inputStream()))
                println("Successfully uploaded ${tableFile.name}!")
            else
                throw Exception("Failure! ${tableFile.name} does not uploaded")
            uploadUpdatedFoodFilesJson()
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
        }
        val format = Json { prettyPrint = true }
        val json = format.encodeToString(foodData)

        if (ftp.uploadFile(foodPath.plus(foodJsonFileName), json.byteInputStream())) {
            println("Successfully uploaded $foodJsonFileName!")
        } else
            throw Exception("Failure! $foodJsonFileName does not uploaded")
    }

    private fun uploadMenuJson(path: String, name: String) {
        val format = Json { prettyPrint = true }
        val menu = Menu(
            path.plus("?").plus(System.currentTimeMillis()),
            name,
            getCurrentMoscowTime()
        )
        val json = format.encodeToString(menu)

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

    private fun getCurrentMoscowTime() =
        LocalDateTime.ofInstant(ZonedDateTime.now().toInstant(), ZoneId.of("Europe/Moscow")).format(timeFormatter)

    private fun parseTimeString(time: String) =
        LocalDateTime.from(timeFormatter.parse(time)).format(timeFormatter)

    private val String.extension: String get() = this.split('.').last()
}