import java.io.File

object FileManager {
    private val tempDir: File
        get() = File(tempDirName).apply { mkdir() }

    val credentialsFile: File
        get() = getResourceFile("credentials.json")

    fun createFile(filename: String) = File(tempDir, filename).apply { createNewFile() }

    fun getResourceFile(path: String): File {
        return File(ClassLoader.getSystemResource(path).file)
    }
}