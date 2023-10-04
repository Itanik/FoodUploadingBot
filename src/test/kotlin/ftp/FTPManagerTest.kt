package ftp

import domain.ftp.FTPManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import readCredentialsFile

internal class FTPManagerTest {
    private lateinit var ftpManager: FTPManager

    @BeforeEach
    fun setUp() {
        ftpManager = FTPManager(readCredentialsFile())
        ftpManager.connect()
    }

    @AfterEach
    fun tearDown() {
        ftpManager.disconnect()
    }

    @Test
    fun getTableFilesList() {
        assertTrue(ftpManager.getTableFilesList().isNotEmpty())
    }

    @Test
    fun getLastAddedFilePath() {
        val file = ftpManager.getLastAddedFile()
        println(file)
        assertNotNull(file)
    }
}