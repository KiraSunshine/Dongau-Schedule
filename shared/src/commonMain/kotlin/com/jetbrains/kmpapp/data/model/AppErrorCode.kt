package com.jetbrains.kmpapp.data.model

enum class AppErrorCode(
    val code: String,
    val shortTitle: String,
    val description: String
) {
    ERR_NO_NETWORK(
        code = "E-101",
        shortTitle = "Нет сети",
        description = "Не удалось подключиться к интернету или серверу"
    ),
    ERR_TIMEOUT(
        code = "E-102",
        shortTitle = "Таймаут",
        description = "Сервер не ответил вовремя"
    ),
    ERR_SERVER_ERROR(
        code = "E-103",
        shortTitle = "Ошибка сервера",
        description = "Сервер расписания временно недоступен (5xx)"
    ),
    ERR_NOT_FOUND(
        code = "E-104",
        shortTitle = "Не найдено",
        description = "Расписание для выбранной группы не найдено"
    ),
    ERR_PARSE_ERROR(
        code = "E-105",
        shortTitle = "Ошибка разбора",
        description = "Не удалось разобрать ответ сервера расписания"
    ),
    ERR_STORAGE_READ(
        code = "E-201",
        shortTitle = "Ошибка чтения",
        description = "Не удалось прочитать сохранённые данные"
    ),
    ERR_STORAGE_WRITE(
        code = "E-202",
        shortTitle = "Ошибка записи",
        description = "Не удалось сохранить данные"
    ),
    ERR_UPDATE_CHECK(
        code = "E-301",
        shortTitle = "Ошибка обновлений",
        description = "Не удалось проверить наличие обновлений"
    ),
    ERR_UNKNOWN(
        code = "E-999",
        shortTitle = "Неизвестная ошибка",
        description = "Произошла непредвиденная ошибка"
    );

    companion object {
        fun fromException(e: Throwable): AppErrorCode {
            var cur: Throwable? = e
            while (cur != null) {
                val name = cur::class.simpleName.orEmpty()
                val full = cur::class.qualifiedName.orEmpty()
                val msg = cur.message?.lowercase().orEmpty()

                if (name.contains("CancellationException") || full.contains("CancellationException")) {
                    return ERR_UNKNOWN
                }

                when {
                    name.contains("Timeout", ignoreCase = true) ||
                        name.contains("SocketTimeout", ignoreCase = true) ||
                        msg.contains("timeout") || msg.contains("timed out") ||
                        msg.contains("request timeout") -> return ERR_TIMEOUT

                    name.contains("UnknownHost", ignoreCase = true) ||
                        name.contains("ConnectException", ignoreCase = true) ||
                        name.contains("UnresolvedAddress", ignoreCase = true) ||
                        name.contains("NoRouteToHost", ignoreCase = true) ||
                        name.contains("IOException", ignoreCase = true) ||
                        (full.contains("ktor", ignoreCase = true) && (msg.contains("connect") || msg.contains("host") || msg.contains("network"))) ||
                        msg.contains("unable to resolve host") ||
                        msg.contains("failed to connect") ||
                        msg.contains("network is unreachable") ||
                        msg.contains("no address associated") ||
                        msg.contains("connection refused") ||
                        msg.contains("connection reset") ||
                        msg.contains("software caused connection abort") ||
                        (msg.contains("network") && !msg.contains("parse")) -> return ERR_NO_NETWORK

                    name.contains("Serialization", ignoreCase = true) ||
                        name.contains("JsonDecoding", ignoreCase = true) ||
                        name.contains("Parse", ignoreCase = true) ||
                        msg.contains("parse") || msg.contains("serialize") || msg.contains("json") ||
                        (msg.contains("expected") && msg.contains("but")) -> return ERR_PARSE_ERROR

                    msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ||
                        msg.contains("internal server error") || msg.contains("bad gateway") -> return ERR_SERVER_ERROR

                    msg.contains("404") || msg.contains("not found") -> return ERR_NOT_FOUND
                }
                cur = cur.cause
            }
            return ERR_UNKNOWN
        }
    }
}
