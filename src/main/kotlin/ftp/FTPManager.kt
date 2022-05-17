package ftp

import data.Credentials
import data.Food
import defaultFormat
import foodPath
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import toLocalDateTime
import java.io.InputStream

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

    fun getTableFilesList(): List<Food> =
        client.listFiles(foodPath)
            .filter { it.name.endsWith("-sm.xlsx") }
            .map { Food(it.name, foodPath.plus(it), it.timestamp.toLocalDateTime().defaultFormat()) }

    fun uploadFile(path: String, file: InputStream): Boolean {
        println("Starting to upload file on: $path")
        client.setFileType(FTP.BINARY_FILE_TYPE)
        return file.use { client.storeFile(path, it) }
    }
}