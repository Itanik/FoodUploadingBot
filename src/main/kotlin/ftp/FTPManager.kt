package ftp

import foodPath
import ftp.data.Credentials
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
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
     */
    fun connect(): Int {
        client.apply {
            connect(credentials.host)
            enterLocalPassiveMode()
            login(credentials.user, credentials.password)
            println("Connected to ${credentials.host}")
            println(replyString)
            return replyCode
        }
    }

    /**
     *  Необходимо вызывать после выполнением любой операции с файлами.
     */
    fun disconnect() {
        client.disconnect()
        println("Disconnected from server")
    }

    fun getTableFilesList(): List<String> =
        client.listFiles(foodPath).map { it.name }.filter { it.endsWith("-sm.xlsx") }

    fun uploadFile(path: String, file: InputStream): Boolean {
        println("Starting to upload file on: $path")
        client.setFileType(FTP.BINARY_FILE_TYPE)
        return file.use { client.storeFile(path, it) }
    }
}