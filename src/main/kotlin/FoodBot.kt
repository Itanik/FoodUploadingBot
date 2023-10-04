import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
import com.github.kotlintelegrambot.entities.files.Document
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel

private object Commands {
    const val STATUS = "status"
    const val START = "start"
    const val DELETE_LAST = "delete_last"
    const val UPDATE_JSON = "update_json"
}

private object ButtonCallback {
    const val MENU_HINT = "menu_hint"
    const val TABLE_HINT = "table_hint"
    const val DELETE_YES = "delete_yes"
    const val DELETE_NO = "delete_no"
    const val REPLACE_TABLE_YES = "replace_table_yes"
    const val REPLACE_TABLE_NO = "replace_table_no"
}

fun foodBot(
    botToken: String,
    allowedUsers: List<String>,
    menuPageUrl: String,
    tablePageUrl: String,
    interactor: WebsiteInteractor,
) = bot {
    token = botToken
    timeout = 30
    logLevel = LogLevel.Network.Body
    var deleteScheduled = false
    var docToReplace: Document? = null

    fun processUploadingResult(result: ProcessingResult, bot: Bot, prevMessage: Message) {
        when (result) {
            ProcessingResult.InProgress ->
                bot.sendMessage(ChatId.fromId(prevMessage.chat.id), Strings.uploadingStarted)

            is ProcessingResult.Success -> {
                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                    listOf(
                        if (result.fileType == FileType.TABLE_FILE)
                            InlineKeyboardButton.Url(
                                text = Strings.checkTableBtnText,
                                url = tablePageUrl,
                            )
                        else
                            InlineKeyboardButton.Url(
                                text = Strings.checkMenuBtnText,
                                url = menuPageUrl,
                            )
                    ),
                )

                bot.sendMessage(
                    prevMessage = prevMessage,
                    messageText = result.data,
                    parseMode = MARKDOWN,
                    replyMarkup = inlineKeyboardMarkup,
                )
            }

            is ProcessingResult.Error -> bot.sendMessage(prevMessage, result.message)
            is ProcessingResult.AlreadyUploaded -> if (result.fileType == FileType.MENU_FILE)
                bot.sendMessage(prevMessage, Strings.menuAlreadyUploaded)
            else
                bot.sendMessage(
                    prevMessage = prevMessage,
                    messageText = Strings.tableAlreadyUploaded,
                    parseMode = MARKDOWN,
                    replyMarkup = createCallbackButtons(
                        mapOf(
                            Strings.tableReplace to ButtonCallback.REPLACE_TABLE_YES,
                            Strings.tableReplaceCancel to ButtonCallback.REPLACE_TABLE_NO,
                        ),
                    ),
                )

            ProcessingResult.ErrorWrongDocumentType -> bot.sendMessage(prevMessage, Strings.wrongFileType)
        }
    }

    dispatch {
        command(Commands.START) {
            if (!allowedUsers.contains(message.chat.username)) {
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.startHintNoPermissions,
                    parseMode = MARKDOWN
                )
                return@command
            }

            bot.sendMessage(
                prevMessage = message,
                messageText = Strings.startHindGreetings,
                parseMode = MARKDOWN,
                replyMarkup = createCallbackButtons(
                    mapOf(
                        Strings.startHintUploadMenuButton to ButtonCallback.MENU_HINT,
                        Strings.startHintUploadTableButton to ButtonCallback.TABLE_HINT,
                    )
                ),
            )

        }

        command(Commands.STATUS) {
            if (!allowedUsers.contains(message.chat.username)) return@command
            interactor.getLastAddedMenu { menu ->
                if (menu == null) {
                    bot.sendMessage(
                        message,
                        Strings.statusMenuFetchFailed
                    )
                    return@getLastAddedMenu
                }
                bot.sendMessage(
                    message,
                    Strings.statusMenuFetchSuccess(menu.name, menu.lastModificationDate)
                )
            }
            interactor.getLastAddedFoodTable { table ->
                if (table == null) {
                    bot.sendMessage(message, Strings.statusTableFetchFailed)
                    return@getLastAddedFoodTable
                }
                bot.sendMessage(
                    message,
                    Strings.statusTableFetchSuccess(table.name, table.lastModificationDate)
                )
            }
        }

        command(Commands.DELETE_LAST) {
            if (message.chat.username != allowedUsers.first()) {
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.noPermissions
                )
                return@command
            }
            if (deleteScheduled) return@command
            deleteScheduled = true

            val lastFile = interactor.getLastAddedFile()

            if (lastFile == null)
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.deleteLastNotFound,
                )
            else
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.deleteLastQuestion.format(lastFile.name),
                    parseMode = MARKDOWN,
                    replyMarkup = createCallbackButtons(
                        mapOf(
                            Strings.deleteLastChoiceYes to ButtonCallback.DELETE_YES,
                            Strings.deleteLastChoiceNo to ButtonCallback.DELETE_NO,
                        )
                    ),
                )
            deleteScheduled = false
        }

        command(Commands.UPDATE_JSON) {
            if (message.chat.username != allowedUsers.first()) {
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.noPermissions
                )
                return@command
            }
            if (interactor.updateJson())
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.updateJsonSuccess
                )
            else
                bot.sendMessage(
                    prevMessage = message,
                    messageText = Strings.updateJsonFailed
                )
        }

        callbackQuery(ButtonCallback.DELETE_YES) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            if (prevMessage.chat.username != allowedUsers.first()) {
                bot.sendMessage(
                    prevMessage = prevMessage,
                    messageText = Strings.noPermissions
                )
                return@callbackQuery
            }
            bot.deleteMessage(ChatId.fromId(prevMessage.chat.id), prevMessage.messageId)
            when (val result = interactor.deleteLastFileOnServer()) {
                is DeletingResult.Success -> bot.sendMessage(
                    prevMessage = prevMessage,
                    messageText = result.message
                )

                is DeletingResult.Error -> bot.sendMessage(
                    prevMessage = prevMessage,
                    messageText = result.message
                )
            }
        }

        callbackQuery(ButtonCallback.DELETE_NO) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            bot.deleteMessage(ChatId.fromId(prevMessage.chat.id), prevMessage.messageId)
        }

        callbackQuery(ButtonCallback.REPLACE_TABLE_YES) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            bot.deleteMessage(ChatId.fromId(prevMessage.chat.id), prevMessage.messageId)
            if (docToReplace == null) {
                bot.sendMessage(prevMessage, Strings.docIsNull)
                return@callbackQuery
            }

            interactor.uploadFile(bot, docToReplace!!, forceUpdate = true) { result ->
                processUploadingResult(result, bot, prevMessage)
            }
        }

        callbackQuery(ButtonCallback.REPLACE_TABLE_NO) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            docToReplace = null
            bot.deleteMessage(ChatId.fromId(prevMessage.chat.id), prevMessage.messageId)
        }

        callbackQuery(ButtonCallback.MENU_HINT) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            bot.sendMessage(prevMessage, Strings.startHintUploadMenuHint)
        }

        callbackQuery(ButtonCallback.TABLE_HINT) {
            val prevMessage = callbackQuery.message ?: return@callbackQuery
            bot.sendMessage(prevMessage, Strings.startHintUploadTableHint)
        }

        photos {
            if (!allowedUsers.contains(message.chat.username)) return@photos
            message.photo?.last()?.let { photo ->

                interactor.uploadMenuPhoto(bot, photo) { result ->
                    processUploadingResult(result, bot, message)
                }
            }
        }

        document {
            if (!allowedUsers.contains(message.chat.username)) return@document
            val document = message.document ?: return@document

            docToReplace = document
            interactor.uploadFile(bot, document) { result ->
                processUploadingResult(result, bot, message)
            }
        }

        telegramError {
            println(error.getErrorMessage())
        }
    }
}

private fun createCallbackButtons(entries: Map<String, String>) = InlineKeyboardMarkup.create(
    listOf(
        entries.map {
            InlineKeyboardButton.CallbackData(
                text = it.key,
                callbackData = it.value
            )
        }
    )
)

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