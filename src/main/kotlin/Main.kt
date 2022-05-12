import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel

private lateinit var allowedUsers: List<String>

fun main(appArgs: Array<String>) {
    val botToken = appArgs.firstOrNull() ?: throw Exception("You should provide telegram token in app args")
    allowedUsers = appArgs.filterNot { it == botToken }
    if (allowedUsers.isEmpty()) throw Exception("You should provide nicknames of users, what will be able to use this bot")
    if (!FileManager.credentialsFile.exists()) throw Exception("You should place credentials.json file in resources folder")

    val interactor = FTPInteractor()

    var menuProcessing = false
    var tableProcessing = false
    var lastProcessedImage = "none"
    var lastProcessedMenuDocument = "none"
    var lastProcessedTableDocument = "none"


    val bot = bot {
        token = botToken
        timeout = 30
        logLevel = LogLevel.Network.Body

        dispatch {
            command("start") {
                if (!hasAccess(update.message?.chat?.username)) {
                    bot.sendMessage(
                        chatId = ChatId.fromId(update.message!!.chat.id),
                        text = Strings.greetingsBad,
                        parseMode = MARKDOWN
                    )
                    return@command
                }

                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData(text = Strings.menuBtnText, callbackData = "menu")),
                    listOf(InlineKeyboardButton.CallbackData(text = Strings.tableBtnText, callbackData = "table"))
                )

                bot.sendMessage(
                    chatId = ChatId.fromId(update.message!!.chat.id),
                    text = Strings.greetingsOk,
                    parseMode = MARKDOWN,
                    replyMarkup = inlineKeyboardMarkup
                )

            }

            callbackQuery("menu") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                bot.sendMessage(ChatId.fromId(chatId), text = Strings.menuClicked)
            }

            callbackQuery("table") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                bot.sendMessage(ChatId.fromId(chatId), text = Strings.tableClicked)
            }

            photos {
                if (!hasAccess(message.chat.username)) return@photos
                bot.sendMessage(ChatId.fromId(message.chat.id), Strings.hintUseFullPhoto)
            }

            document {
                if (!hasAccess(message.chat.username)) return@document
                val document = message.document ?: return@document

                println("Attached document: $document")
                val extension = document.fileName?.split('.')?.last() ?: return@document
                when (extension) {
                    "jpg", "jpeg", "png" -> interactor.downloadFile(bot, document) {

                    }
                }
                /*if (photo.fileId == lastProcessedImage) {
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = Strings.photoAlreadyProcessed
                    )
                    return@photos
                }
                message.chat.type*/

                /*interactor.downloadFile(bot, photo.fileId) { file ->
                    if (file == null) {
                        println("Cannot load file")
                        return@downloadFile
                    }
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "Успешно скачал файл ${file.name}"
                    )
                }
                lastProcessedImage = photo.fileId*/
            }

            telegramError {
                println(error.getErrorMessage())
            }
        }
    }

    bot.startPolling()
}

private fun hasAccess(username: String?) =
    if (username == null || username.isBlank())
        false
    else
        allowedUsers.contains(username)