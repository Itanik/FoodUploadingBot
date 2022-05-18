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
    if (allowedUsers.isEmpty())
        throw Exception("You should provide nicknames of users, what will be able to use this bot")
    if (!FileManager.credentialsFile.exists())
        throw Exception("You should place credentials.json file in app root folder (from where you run it)")

    val interactor = WebsiteInteractor()

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
                                            url = interactor.credentials.menuPage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    chatId = ChatId.fromId(update.message!!.chat.id),
                                    text = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                            is ProcessingResult.Error ->
                                bot.sendMessage(ChatId.fromId(message.chat.id), result.message)
                            is ProcessingResult.AlreadyUploaded ->
                                bot.sendMessage(ChatId.fromId(message.chat.id), result.message)
                            else ->
                                bot.sendMessage(
                                    ChatId.fromId(message.chat.id),
                                    "Что-то пошло не так при обработке фото..."
                                )
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
                            bot.sendMessage(ChatId.fromId(message.chat.id), "Начинаю загрузку на сайт")
                        is ProcessingResult.Success -> when (result.fileType) {
                            FileType.MENU -> {
                                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        InlineKeyboardButton.Url(
                                            text = Strings.checkMenuBtnText,
                                            url = interactor.credentials.menuPage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    chatId = ChatId.fromId(update.message!!.chat.id),
                                    text = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                            FileType.TABLE -> {
                                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                                    listOf(
                                        InlineKeyboardButton.Url(
                                            text = Strings.checkTableBtnText,
                                            url = interactor.credentials.tablePage
                                        )
                                    ),
                                )

                                bot.sendMessage(
                                    chatId = ChatId.fromId(update.message!!.chat.id),
                                    text = result.data,
                                    parseMode = MARKDOWN,
                                    replyMarkup = inlineKeyboardMarkup
                                )
                            }
                        }

                        is ProcessingResult.ErrorWrongDocumentType ->
                            bot.sendMessage(ChatId.fromId(message.chat.id), "Неверный формат файла.")
                        is ProcessingResult.Error ->
                            bot.sendMessage(ChatId.fromId(message.chat.id), result.message)
                        is ProcessingResult.AlreadyUploaded ->
                            bot.sendMessage(ChatId.fromId(message.chat.id), result.message)
                    }
                }
            }

            command("status") {
                if (!hasAccess(message.chat.username)) return@command
                interactor.getLastAddedMenu { menu ->
                    if (menu == null) {
                        bot.sendMessage(
                            ChatId.fromId(message.chat.id),
                            "Не удалось получить информацию о последнем файле меню"
                        )
                        return@getLastAddedMenu
                    }
                    bot.sendMessage(
                        ChatId.fromId(message.chat.id),
                        "Последний файл меню:\n\n" +
                                "Имя - ${menu.name ?: "Неизвестно"}\n\n" +
                                "Дата загрузки - ${menu.lastModificationDate ?: "Неизвестно"}"
                    )
                }
                interactor.getLastAddedFoodTable { table ->
                    if (table == null) {
                        bot.sendMessage(
                            ChatId.fromId(message.chat.id),
                            "Не удалось получить информацию по последней таблице"
                        )
                        return@getLastAddedFoodTable
                    }
                    bot.sendMessage(
                        ChatId.fromId(message.chat.id),
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