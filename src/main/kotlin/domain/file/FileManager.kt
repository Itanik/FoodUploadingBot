package domain.file

import misc.tempDirName
import java.io.File

object FileManager {
    private val tempDir: File
        get() = File(tempDirName).apply { mkdir() }

    val credentialsFile: File
        get() = File("data/credentials.json")

    fun createFile(filename: String) = File(tempDir, filename).apply { createNewFile() }

    fun deleteFile(file: File): Boolean = file.delete()
}