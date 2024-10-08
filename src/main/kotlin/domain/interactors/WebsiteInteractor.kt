package domain.interactors

import misc.Strings.deleteLastSuccess
import misc.Strings.deleteLastFailed
import misc.Strings.menuUploadedSuccessfully
import misc.Strings.tableUploadedSuccessfully
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.files.Document
import com.github.kotlintelegrambot.entities.files.PhotoSize
import data.Credentials
import data.Food
import data.Menu
import domain.file.FileManager
import misc.dateTimeFormat
import misc.foodImageFileName
import misc.foodJsonFileName
import misc.foodPath
import domain.ftp.FTPManager
import domain.network.WebsiteHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import misc.menuJsonFileName
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class FileType {
    MENU_PHOTO, MENU_FILE, TABLE_FILE
}

sealed class UploadingResult {
    data class Success(val data: String, val fileType: FileType) : UploadingResult()
    object InProgress : UploadingResult()
    object ErrorWrongDocumentType : UploadingResult()
    data class Error(val message: String, val fileType: FileType) : UploadingResult()
    data class AlreadyUploaded(val fileType: FileType) : UploadingResult()
}

sealed class DeletingResult {
    data class Success(val message: String) : DeletingResult()
    data class Error(val message: String) : DeletingResult()
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

    fun uploadMenuPhoto(bot: Bot, photoSize: PhotoSize, onResult: (UploadingResult) -> Unit) = scope.launch(Default) {
        onResult(UploadingResult.InProgress)
        val file = downloadPhoto(bot, photoSize)
        if (file == null) {
            onResult(UploadingResult.Error("Ошибка при скачивании файла", FileType.MENU_PHOTO))
            return@launch
        }

        val result = uploadMenu(file)
        onResult(result)
    }

    fun uploadFile(bot: Bot, document: Document, forceUpdate: Boolean = false, onResult: (UploadingResult) -> Unit) =
        scope.launch(Default) {
            when (document.fileName?.extension?.lowercase() ?: "") {
                "jpg", "jpeg", "png", "heic", "pdf" -> {
                    try {
                        if (!forceUpdate && isMenuAlreadyUploaded(document.fileName!!)) {
                            onResult(UploadingResult.AlreadyUploaded(FileType.MENU_FILE))
                            return@launch
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onResult(UploadingResult.Error("Ошибка: ${e.message}", FileType.MENU_FILE))
                    }

                    onResult(UploadingResult.InProgress)

                    val file = downloadFile(bot, document)
                    if (file == null) {
                        onResult(UploadingResult.Error("Ошибка при скачивании файла", FileType.MENU_FILE))
                        return@launch
                    }

                    val result = uploadMenu(file)
                    onResult(result)
                }

                "xlsx" -> {
                    try {
                        if (!forceUpdate && isTableAlreadyUploaded(document.fileName!!)) {
                            onResult(UploadingResult.AlreadyUploaded(FileType.TABLE_FILE))
                            return@launch
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onResult(UploadingResult.Error("Ошибка: ${e.message}", FileType.TABLE_FILE))
                    }

                    onResult(UploadingResult.InProgress)

                    val file = downloadFile(bot, document)
                    if (file == null) {
                        onResult(UploadingResult.Error("Ошибка при скачивании файла", FileType.TABLE_FILE))
                        return@launch
                    }
                    val result = uploadTable(file)
                    onResult(result)
                }

                else -> {
                    onResult(UploadingResult.ErrorWrongDocumentType)
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

    private fun uploadMenu(menuFile: File): UploadingResult =
        ftpManager.commit(onError = { e -> UploadingResult.Error("Ошибка: ${e.message}", FileType.MENU_FILE) }) {
            val path = uploadMenuFile(menuFile)
            uploadMenuJson(path, menuFile.name)
            deleteFile(menuFile)

            UploadingResult.Success(menuUploadedSuccessfully, FileType.MENU_FILE)
        }

    private fun uploadMenuFile(menuFile: File): String {
        val fileName = menuFile.name
        val newName = foodImageFileName.plus(fileName.extension)
        val newPath = foodPath.plus(newName)

        val isFileUploaded = ftpManager.uploadFile(newPath, menuFile.inputStream())
        if (isFileUploaded) println("Successfully uploaded $newName!")
        else throw Exception("Не удалось загрузить файл меню")
        return newPath
    }

    private fun uploadMenuJson(path: String, name: String) {
        val format = Json { prettyPrint = true }
        val menu = Menu(
            path.plus("?").plus(System.currentTimeMillis()), name, getCurrentMoscowTime()
        )
        val json = format.encodeToString(menu)

        val isJsonUploaded = ftpManager.uploadFile(foodPath.plus(menuJsonFileName), json.byteInputStream())
        if (isJsonUploaded) println("Successfully uploaded $menuJsonFileName!")
        else throw Exception("Не могу обновить метафайл с данными меню")
    }

    private suspend fun isTableAlreadyUploaded(tableName: String) = try {
        websiteHttpClient.getTable().any { it.name == tableName }
    } catch (e: Exception) {
        throw Exception("Не могу проверить последнюю загруженную таблицу")
    }

    private fun uploadTable(tableFile: File): UploadingResult = ftpManager.commit(
        onError = { e -> UploadingResult.Error("Ошибка: ${e.message}", FileType.TABLE_FILE) },
    ) {
        val isFileUploaded = ftpManager.uploadFile(foodPath.plus(tableFile.name), tableFile.inputStream())
        if (isFileUploaded) {
            println("Successfully uploaded ${tableFile.name}!")
            uploadUpdatedFoodFilesJson()
            deleteFile(tableFile)
            UploadingResult.Success(tableUploadedSuccessfully, FileType.TABLE_FILE)
        } else UploadingResult.Error("Не удалось загрузить файл таблицы", FileType.TABLE_FILE)
    }

    fun getLastAddedFile(): Food? = ftpManager.commit(onError = { _ -> null }) {
        ftpManager.getLastAddedFile()
    }

    fun deleteLastFileOnServer(): DeletingResult = ftpManager.commit(
        onError = { e -> DeletingResult.Error(deleteLastFailed.format(", причина: ${e.message}")) },
    ) {
        val lastAddedFile =
            ftpManager.getLastAddedFile() ?: return DeletingResult.Error(deleteLastFailed.format(", файл не найден"))

        if (!ftpManager.deleteFile(lastAddedFile.path)) DeletingResult.Error(deleteLastFailed.format(""))

        uploadUpdatedFoodFilesJson()
        DeletingResult.Success(deleteLastSuccess.format(lastAddedFile.name))
    }

    fun updateJson(): Boolean = ftpManager.commit(onError = { _ -> false }) {
        uploadUpdatedFoodFilesJson()
        true
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
        if (isJsonUploaded) println("Successfully uploaded $foodJsonFileName!")
        else throw Exception("Не могу обновить метафайл с данными таблиц")
    }

    private fun getCurrentMoscowTime() =
        LocalDateTime.ofInstant(ZonedDateTime.now().toInstant(), ZoneId.of("Europe/Moscow")).format(timeFormatter)

    private val String.extension: String get() = this.split('.').last()
}
