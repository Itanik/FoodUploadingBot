import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import data.Credentials
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private lateinit var allowedUsers: List<String>

fun main() {
    val credentials = readCredentialsFile()
    val botToken = credentials.token
    allowedUsers = credentials.allowedUsers
    if (allowedUsers.isEmpty())
        throw Exception("You should provide nicknames of users, what will be able to use this bot")

    val interactor = WebsiteInteractor(credentials)

    val bot = bot {
        token = botToken
        timeout = 30
        logLevel = LogLevel.Network.Body

        dispatch {
            command("start") {
                if (!hasAccess(update.message?.chat?.username)) {
                    bot.sendMessage(
                        prevMessage = message,
                        messageText = Strings.greetingsBad,
                        parseMode = MARKDOWN
                    )
                    return@command
                }

                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData(text = Strings.menuBtnText, callbackData = "menu")),
                    listOf(InlineKeyboardButton.CallbackData(text = Strings.tableBtnText, callbackData = "table"))
                )

                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.greetingsOk,
                    parseMode = MARKDOWN,
                    replyMarkup = inlineKeyboardMarkup
                )

            }

            callbackQuery("menu") {
                val prevMessage = callbackQuery.message ?: return@callbackQuery
                bot.sendMessage(prevMessage, Strings.menuClicked)
            }

            callbackQuery("table") {
                val prevMessage = callbackQuery.message ?: return@callbackQuery
                bot.sendMessage(prevMessage, Strings.tableClicked)
            }

            photos {
                if (!hasAccess(message.chat.username)) return@photos
                message.photo?.last()?.let { photo ->

                    interactor.processPhoto(bot, photo) { result ->
                        when (result) {
                            is ProcessingResult.InProgress ->
                                bot.sendMessage(ChatId.fromId(message.chat.id), "Начинаю загрузку на сайт")
                            is ProcessingResult.Success -> {
                                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        InlineKeyboardButton.Url(
                                            text = Strings.checkMenuBtnText,
                                            url = credentials.menuPage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    prevMessage = message,
                                    messageText = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                            is ProcessingResult.Error ->
                                bot.sendMessage(message, result.message)
                            is ProcessingResult.AlreadyUploaded ->
                                bot.sendMessage(message, result.message)
                            else ->
                                bot.sendMessage(message, "Что-то пошло не так при обработке фото...")
                        }
                    }
                }
//                bot.sendMessage(ChatId.fromId(message.chat.id), Strings.hintUseFullPhoto)
            }

            document {
                if (!hasAccess(message.chat.username)) return@document
                val document = message.document ?: return@document

                interactor.processFile(bot, document) { result ->
                    when (result) {
                        is ProcessingResult.InProgress ->
                            bot.sendMessage(message, "Начинаю загрузку на сайт")
                        is ProcessingResult.Success -> when (result.fileType) {
                            FileType.MENU -> {
                                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        InlineKeyboardButton.Url(
                                            text = Strings.checkMenuBtnText,
                                            url = credentials.menuPage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    prevMessage = message,
                                    messageText = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                            FileType.TABLE -> {
                                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        InlineKeyboardButton.Url(
                                            text = Strings.checkTableBtnText,
                                            url = credentials.tablePage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    prevMessage = message,
                                    messageText = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                        }

                        is ProcessingResult.ErrorWrongDocumentType ->
                            bot.sendMessage(message, "Неверный формат файла.")
                        is ProcessingResult.Error ->
                            bot.sendMessage(message, result.message)
                        is ProcessingResult.AlreadyUploaded ->
                            bot.sendMessage(message, result.message)
                    }
                }
            }

            command("status") {
                if (!hasAccess(message.chat.username)) return@command
                interactor.getLastAddedMenu { menu ->
                    if (menu == null) {
                        bot.sendMessage(
                            message,
                            "Не удалось получить информацию о последнем файле меню"
                        )
                        return@getLastAddedMenu
                    }
                    bot.sendMessage(
                        message,
                        "Последний файл меню:\n\n" +
                                "Имя - ${menu.name ?: "Неизвестно"}\n\n" +
                                "Дата загрузки - ${menu.lastModificationDate ?: "Неизвестно"}"
                    )
                }
                interactor.getLastAddedFoodTable { table ->
                    if (table == null) {
                        bot.sendMessage(message, "Не удалось получить информацию по последней таблице")
                        return@getLastAddedFoodTable
                    }
                    bot.sendMessage(
                        message,
                        "Последняя таблица:\n\n" +
                                "Имя - ${table.name}\n\n" +
                                "Дата загрузки - ${table.lastModificationDate ?: "Неизвестно"}"
                    )
                }
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

private fun readCredentialsFile(): Credentials {
    if (!FileManager.credentialsFile.exists())
        throw Exception("You should place credentials.json file in app root folder (from where you run it)")
    val credJson = FileManager.credentialsFile.bufferedReader().use {
        it.readText()
    }
    return Json.decodeFromString(credJson)
}

private fun Bot.sendMessage(
    prevMessage: Message,
    messageText: String,
    parseMode: ParseMode? = null,
    replyMarkup: InlineKeyboardMarkup? = null
) {
    println(
        "Sending message to user ${prevMessage.chat.username}. " +
                "Text: $messageText. " +
                "Parse mode: ${parseMode != null}. " +
                "Reply markup: ${replyMarkup != null}"
    )
    sendMessage(
        chatId = ChatId.fromId(prevMessage.chat.id),
        text = messageText,
        parseMode = parseMode,
        replyMarkup = replyMarkup
    )
}