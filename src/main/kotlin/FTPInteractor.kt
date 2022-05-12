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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.net.ftp.FTPReply
import java.io.File

sealed class ProcessingResult {
    data class Success(val data: String) : ProcessingResult()
    object InProgress : ProcessingResult()
    object ErrorWrongDocumentType : ProcessingResult()
    data class Error(val message: String) : ProcessingResult()
}

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

    private fun downloadFile(bot: Bot, document: Document): File? {
        val bytes = bot.downloadFileBytes(document.fileId) ?: return null
        return FileManager.createFile(document.fileName!!).apply { writeBytes(bytes) }
    }

    private fun uploadMenu(menuFile: File) {
        try {
            val connectionResult = ftp.connect()
            if (!FTPReply.isPositiveCompletion(connectionResult)) throw Exception("Server refuse connection")
            val path = uploadMenuFile(menuFile)
            uploadMenuJson(path)
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
            Food(it, foodPath.plus(it))
        }
        val format = Json { prettyPrint = true }
        val json = format.encodeToString(foodData)

        if (ftp.uploadFile(foodPath.plus(foodJsonFileName), json.byteInputStream())) {
            println("Successfully uploaded $foodJsonFileName!")
        } else
            throw Exception("Failure! $foodJsonFileName does not uploaded")
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
