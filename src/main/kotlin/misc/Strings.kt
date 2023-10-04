package misc

object Strings {
    // Commands
    const val noPermissions = "У вас недостаточно прав для выполнения этой операции"

    // 'Start' command
    const val startHintUploadMenuButton = "Загрузить меню"
    const val startHintUploadTableButton = "Загрузить таблицу"
    const val startHintUploadMenuHint =
        "Теперь отправь мне файл с меню. Это может быть изображение или pdf файл. Если фото, " +
                "то его нужно загрузить несжатым, как файл"
    const val startHintUploadTableHint = "Теперь отправь мне файл с таблицей. Это должен быть файл с расширением xlsx"

    const val startHindGreetings = "Привет! Я помогу тебе загрузить ежедневное меню и таблицы на сайт. \n\n" +
            "Нажми на кнопку _\"$startHintUploadMenuButton\"_, затем прикрепи файл, чтобы загрузить меню. \n\n" +
            "Нажми на кнопку _\"$startHintUploadTableButton\"_, затем прикрепи файл, чтобы загрузить таблицу."
    const val startHintNoPermissions = "Похоже вы не имеете доступа к моему функционалу ¯\\_( ͡° ͜ʖ ͡°)_/¯"

    // 'Status' command
    const val statusMenuFetchFailed = "Не удалось получить информацию о последнем файле меню"
    const val statusTableFetchFailed = "Не удалось получить информацию по последней таблице"
    fun statusMenuFetchSuccess(name: String?, lastModificationDate: String?) = "Последний файл меню:\n\n" +
            displayNameAndDate(name, lastModificationDate)

    fun statusTableFetchSuccess(name: String?, lastModificationDate: String?) = "Последняя таблица:\n\n" +
            displayNameAndDate(name, lastModificationDate)

    private fun displayNameAndDate(name: String?, lastModificationDate: String?) = "Имя: ${name ?: "Неизвестно"}\n\n" +
            "Дата загрузки: ${lastModificationDate ?: "Неизвестно"}"

    // 'Update json' command
    const val updateJsonSuccess = "Json файл успешно обновлен"
    const val updateJsonFailed = "Ошибка при обновлении"

    // 'Delete last' command
    const val deleteLastNotFound = "Не могу найти последний файл"
    const val deleteLastQuestion = "Вы уверены, что хотите удалить файл \"%s\"?"
    const val deleteLastChoiceYes = "Да"
    const val deleteLastChoiceNo = "Нет"
    const val deleteLastSuccess = "Файл %s успешно удален"
    const val deleteLastFailed = "Не удалось удалить файл%s"

    // File handling
    const val checkMenuBtnText = "Проверить меню"
    const val checkTableBtnText = "Проверить таблицу"
    const val uploadingStarted = "Начинаю загрузку на сайт"

    const val docIsNull = "Не могу найти документ. Попробуйте снова"
    const val menuAlreadyUploaded = "Это меню уже загружено"
    const val tableUploadedSuccessfully = "Таблица успешно загружена"

    const val tableAlreadyUploaded = "Данная таблица уже загружена. Желаете её заменить?"
    const val tableReplace = "Заменить"
    const val tableReplaceCancel = "Отмена"

    const val menuUploadedSuccessfully = "Меню успешно загружено"

    const val wrongFileType = "Неверный формат файла."
}