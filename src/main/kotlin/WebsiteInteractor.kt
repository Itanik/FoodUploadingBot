import Strings.menuAlreadyProcessed
import Strings.menuUploadedSuccessfully
import Strings.tableAlreadyProcessed
import Strings.tableUploadedSuccessfully
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.files.Document
import com.github.kotlintelegrambot.entities.files.PhotoSize
import data.Credentials
import data.Food
import data.Menu
import ftp.FTPManager
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class FileType {
    MENU, TABLE
}

sealed class ProcessingResult {
    data class Success(val data: String, val fileType: FileType) : ProcessingResult()
    object InProgress : ProcessingResult()
    object ErrorWrongDocumentType : ProcessingResult()
    data class Error(val message: String) : ProcessingResult()
    data class AlreadyUploaded(val message: String) : ProcessingResult()
}

/**
 *  Выполняет функции общения с сайтом, адрес которого указан в credentials.json
 */
class WebsiteInteractor(credentials: Credentials) {
    private val scope = MainScope()
    private val ftpManager = FTPManager(credentials)
    private val timeFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern(dateTimeFormat)
    }
    private val websiteHttpClient = WebsiteHttpClient(credentials)

    fun processPhoto(bot: Bot, photoSize: PhotoSize, onResult: (ProcessingResult) -> Unit) = scope.launch(Default) {
        onResult(ProcessingResult.InProgress)
        val file = downloadPhoto(bot, photoSize)
        if (file == null) {
            onResult(ProcessingResult.Error("Ошибка при скачивании файла"))
            return@launch
        }

        val result = uploadMenu(file)
        onResult(result)
    }

    fun processFile(bot: Bot, document: Document, onResult: (ProcessingResult) -> Unit) = scope.launch(Default) {
        when (document.fileName?.extension?.lowercase() ?: "") {
            "jpg", "jpeg", "png", "heic", "pdf" -> {
                try {
                    if (isMenuAlreadyUploaded(document.fileName!!)) {
                        onResult(ProcessingResult.AlreadyUploaded(menuAlreadyProcessed))
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(ProcessingResult.Error("Ошибка: ${e.message}"))
                }

                onResult(ProcessingResult.InProgress)

                val file = downloadFile(bot, document)
                if (file == null) {
                    onResult(ProcessingResult.Error("Ошибка при скачивании файла"))
                    return@launch
                }

                val result = uploadMenu(file)
                onResult(result)
            }
            "xlsx" -> {
                try {
                    if (isTableAlreadyUploaded(document.fileName!!)) {
                        onResult(ProcessingResult.AlreadyUploaded(tableAlreadyProcessed))
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(ProcessingResult.Error("Ошибка: ${e.message}"))
                }

                onResult(ProcessingResult.InProgress)

                val file = downloadFile(bot, document)
                if (file == null) {
                    onResult(ProcessingResult.Error("Ошибка при скачивании файла"))
                    return@launch
                }
                val result = uploadTable(file)
                onResult(result)
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

    private fun downloadPhoto(bot: Bot, photoSize: PhotoSize): File? {
        val bytes = bot.downloadFileBytes(photoSize.fileId) ?: return null
        return FileManager.createFile(photoSize.fileId.plus(".jpg")).apply { writeBytes(bytes) }
    }

    private suspend fun isMenuAlreadyUploaded(menuFileName: String): Boolean = try {
        websiteHttpClient.getMenu().name == menuFileName
    } catch (e: Exception) {
        throw Exception("Не могу проверить последнее загруженное меню")
    }

    private fun uploadMenu(menuFile: File): ProcessingResult {
        return try {
            ftpManager.connect()

            val path = uploadMenuFile(menuFile)
            uploadMenuJson(path, menuFile.name)
            deleteFile(menuFile)

            ProcessingResult.Success(menuUploadedSuccessfully, FileType.MENU)
        } catch (e: Exception) {
            e.printStackTrace()
            ProcessingResult.Error("Ошибка: ${e.message}")
        } finally {
            ftpManager.disconnect()
        }
    }

    private fun uploadMenuFile(menuFile: File): String {
        val fileName = menuFile.name
        val newName = foodImageFileName.plus(fileName.extension)
        val newPath = foodPath.plus(newName)

        val isFileUploaded = ftpManager.uploadFile(newPath, menuFile.inputStream())
        if (isFileUploaded)
            println("Successfully uploaded $newName!")
        else
            throw Exception("Не удалось загрузить файл меню")
        return newPath
    }

    private fun uploadMenuJson(path: String, name: String) {
        val format = Json { prettyPrint = true }
        val menu = Menu(
            path.plus("?").plus(System.currentTimeMillis()),
            name,
            getCurrentMoscowTime()
        )
        val json = format.encodeToString(menu)

        val isJsonUploaded = ftpManager.uploadFile(foodPath.plus(menuJsonFileName), json.byteInputStream())
        if (isJsonUploaded)
            println("Successfully uploaded $menuJsonFileName!")
        else
            throw Exception("Не могу обновить метафайл с данными меню")
    }

    private suspend fun isTableAlreadyUploaded(tableName: String) = try {
        websiteHttpClient.getTable().any { it.name == tableName }
    } catch (e: Exception) {
        throw Exception("Не могу проверить последнюю загруженную таблицу")
    }

    private fun uploadTable(tableFile: File): ProcessingResult {
        return try {
            ftpManager.connect()

            val isFileUploaded = ftpManager.uploadFile(foodPath.plus(tableFile.name), tableFile.inputStream())
            if (isFileUploaded) {
                println("Successfully uploaded ${tableFile.name}!")
                uploadUpdatedFoodFilesJson()
                deleteFile(tableFile)
                ProcessingResult.Success(tableUploadedSuccessfully, FileType.TABLE)
            } else
                ProcessingResult.Error("Не удалось загрузить файл таблицы")

        } catch (e: Exception) {
            e.printStackTrace()
            ProcessingResult.Error("Ошибка: ${e.message}")
        } finally {
            ftpManager.disconnect()
        }
    }

    private fun deleteFile(file: File) {
        FileManager.deleteFile(file)
    }

    private fun uploadUpdatedFoodFilesJson() {
        val foodFiles = ftpManager.getTableFilesList()
        println("Total number of food files: ${foodFiles.size}")
        val format = Json { prettyPrint = true }
        val json = format.encodeToString(foodFiles)

        val isJsonUploaded = ftpManager.uploadFile(foodPath.plus(foodJsonFileName), json.byteInputStream())
        if (isJsonUploaded)
            println("Successfully uploaded $foodJsonFileName!")
        else
            throw Exception("Не могу обновить метафайл с данными таблиц")
    }

    private fun getCurrentMoscowTime() =
        LocalDateTime.ofInstant(ZonedDateTime.now().toInstant(), ZoneId.of("Europe/Moscow")).format(timeFormatter)

    private val String.extension: String get() = this.split('.').last()
}
