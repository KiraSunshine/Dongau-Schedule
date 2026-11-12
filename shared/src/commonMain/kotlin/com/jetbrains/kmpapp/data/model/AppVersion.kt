package com.jetbrains.kmpapp.data.model

object AppVersion {
    /**
     * Базовая версия текущей линии разработки в формате YY.X.Z (старт: 26.0.0).
     * CI подставляет полный VERSION_NAME по каналу:
     *  - тег v26.0.0 / v26.0.1      → stable
     *  - тег v26.0.0-beta.3 / -rc.1  → beta / rc (prerelease)
     *  - push в main                 → 26.X-dev.N (rolling preview)
     *  - contributor build           → 26.X-contrib.N
     * После стабильного релиза v26.0.0 руками поднимается до 26.1.0.
     */
    const val RELEASE_VERSION = "27.0.1"
    const val VERSION_NAME = "27.0.1"

    /** stable | beta | rc | dev | contrib — подставляет CI через tools/versioning.py */
    const val BUILD_CHANNEL = "stable"

    /**
     * Числовой код сборки. CI подставляет epoch-секунды начала запуска
     * (github.run_started_at): монотонно во всех каналах, влезает в
     * Int32 / Android versionCode (max 2147483647). github.run_id НЕ подходит.
     */
    const val BUILD_NUMBER = 1794512178
    const val COMMIT_SHA = "3e6fdaa"

    /** Стабильный канал обновлений (обновляется только стабильными релизами). */
    const val UPDATE_FEED_URL = "https://raw.githubusercontent.com/KiraSunshine/Dongau-Schedule/gh-pages/version.json"

    /** Канал бета-версий (beta/rc): проверяется только если включён «Бета-канал» в настройках. */
    const val BETA_FEED_URL = "https://raw.githubusercontent.com/KiraSunshine/Dongau-Schedule/gh-pages/beta.json"

    const val IS_CRITICAL = false
    const val MIN_SUPPORTED_BUILD = 1
    const val CHANGELOG = "Исправлено окно экспорта/поделиться при большом числе предметов; чередование отметок «каждые N недель»; улучшена диагностика и классификация ошибок обновления расписания (E-999) и обработка null-полей ответа сервера; ограничение добавления пар раньше сегодняшнего дня (только если до сегодня нет пар и дата не в семестре старше текущего); прочие улучшения и исправления."

    val isTestBuild: Boolean get() = BUILD_CHANNEL != "stable"

    const val APPLICATION_ID = "ru.dongau.schedule"
    const val DISPLAY_VERSION = "Версия $VERSION_NAME (сборка $BUILD_NUMBER)"
    const val GITHUB_REPO = "KiraSunshine/Dongau-Schedule"
    const val GITHUB_REPO_URL = "https://github.com/KiraSunshine/Dongau-Schedule"
    const val GITHUB_ISSUES_URL = "https://github.com/KiraSunshine/Dongau-Schedule/issues"
    const val DEVELOPER_NAME = "KiraSunshine"

}
