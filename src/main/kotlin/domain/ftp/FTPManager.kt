package domain.ftp

import data.Credentials
import data.Food
import misc.defaultFormat
import misc.foodPath
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import misc.toLocalDateTime
import java.io.InputStream
import java.time.LocalDateTime

/**
 * Получает информацию о загруженных на сайт файлах и загружает туда новые файлы.
 */
class FTPManager(private val credentials: Credentials) {
    private val client: FTPClient by lazy {
        FTPClient().apply {
            configure(FTPClientConfig())
        }
    }

    /**
     * Необходимо вызывать перед выполнением любой операции с файлами.
     * @throws Exception если не удалось подключиться к серверу
     */
    fun connect() {
        client.apply {
            connect(credentials.host)
            enterLocalPassiveMode()
            login(credentials.user, credentials.password)
            println(replyString)
            if (!FTPReply.isPositiveCompletion(replyCode))
                throw Exception("Сервер отказал в подключении")
            println("Connected to ${credentials.host}")
        }
    }

    /**
     *  Необходимо вызывать после выполнения любой операции с файлами.
     */
    fun disconnect() {
        client.disconnect()
        println("Disconnected from server")
    }

    /**
     * Возвращает все хранимые по пути host/food файлы таблиц
     */
    fun getTableFilesList(): List<Food> =
        client.listFiles(foodPath)
            .filter { it.name.endsWith("-sm.xlsx") }
            .map { Food(it.name, foodPath.plus(it.name), it.timestamp.toLocalDateTime().defaultFormat()) }

    /**
     * Возвращает последний загруженный на сервер Food файл
     */
    fun getLastAddedFile(): Food? =
        getTableFilesList()
            .map { it to parseDateTimeString(it.lastModificationDate) }
            .sortedBy { (_, date) -> date }
            .lastOrNull()
            ?.first

    private fun parseDateTimeString(formattedTime: String?): LocalDateTime? {
        if (formattedTime == null) return null

        return try {
            LocalDateTime.parse(formattedTime)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Удаляет файл на сервере по заданному пути.
     *  @return true если успешно, false если нет
     */
    fun deleteFile(path: String) = client.deleteFile(path)

    /**
     * Загружает файл по пути host/path
     */
    fun uploadFile(path: String, file: InputStream): Boolean {
        println("Starting to upload file on: $path")
        client.setFileType(FTP.BINARY_FILE_TYPE)
        return file.use { client.storeFile(path, it) }
    }

    inline fun commit(action: ()-> Unit) {
        try {
            connect()
            action()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            disconnect()
        }
    }
}