# ДонГУ Расписание (Dongau Schedule)

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-3DDC84.svg?logo=android&logoColor=white)](https://github.com/KiraSunshine/Dongau-Schedule/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.0-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)

Кроссплатформенное мобильное приложение: расписание учебных занятий для студентов и преподавателей ДонГУ (Донецкий Государственный Университет).

> [!NOTE]
> **Проект неофициальный.** Не аффилирован с ДонГУ. Название вуза и все данные (расписание) принадлежат их правообладателям; приложение только отображает их со ссылкой на официальные источники.

**Скачать:** [APK (Android, стабильная версия)](https://github.com/KiraSunshine/Dongau-Schedule/releases/latest/download/Schedule-Dongau.apk) · [IPA (iOS, без подписи)](https://github.com/KiraSunshine/Dongau-Schedule/releases/latest/download/Schedule-Dongau.ipa) · [Все релизы](https://github.com/KiraSunshine/Dongau-Schedule/releases)

---

## Возможности

**Расписание**
- Поиск и отображение расписания групп, преподавателей и аудиторий.
- Нумерация недель семестра, чётные/нечётные недели.
- Индикация текущего занятия и прогресс до его конца.
- Сохранение нескольких расписаний с переключением между ними.
- Подсветка изменений при обновлении расписания.
- Локальный кэш: расписание доступно без сети.

**Задачи**
- Учёт задач по предметам: категории (лабораторные, практики, домашние задания, курсовые и др.), приоритеты, статусы, чеклисты подзадач.

**Интерфейс**
- Темы: светлая, тёмная, системная.
- Настраиваемый плавающий док: порядок и видимость разделов.
- Встроенная проверка обновлений.

---

## Установка

### Android
1. Скачайте [Schedule-Dongau.apk](https://github.com/KiraSunshine/Dongau-Schedule/releases/latest/download/Schedule-Dongau.apk) из последнего стабильного релиза.
2. Установите, разрешив установку из неизвестных источников.
3. Дальше приложение проверяет обновления само и предлагает установить новый APK.

### iOS
IPA собирается без подписи (unsigned) — для установки переподпишите его любым инструментом sideload.

Источник приложений (AltStore-совместимый формат):

```
https://raw.githubusercontent.com/KiraSunshine/Dongau-Schedule/gh-pages/apps.json
```

---

## Сборка из исходников

Требования: JDK 21, Android SDK (compileSdk 37, minSdk 24, targetSdk 37), Xcode 16+ (для iOS). Gradle 9.6.1 подключается через wrapper (`gradlew`).

```bash
git clone https://github.com/KiraSunshine/Dongau-Schedule.git
cd Dongau-Schedule

# Android (debug APK)
./gradlew assembleDebug
# результат: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# iOS (unsigned, на macOS)
cd iosApp
xcodebuild -scheme iosApp -configuration Release -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
```

Тесты общего кода: `./gradlew :shared:allTests`.

Стек: Kotlin Multiplatform, Compose Multiplatform, Ktor Client, kotlinx.serialization, Room/SQLite, Multiplatform Settings.

---

## Источники данных

- **Расписание:** публичный API расписания ДонГУ ([edu.dongau.ru](https://edu.dongau.ru) / `dongau-ems.com`).
- Приложение не отправляет данные пользователей: сетевые запросы — только чтение (API расписания и GitHub: проверка обновлений, список контрибьюторов).

## Лицензия

Код проекта распространяется по [GNU GPL v3](LICENSE).
Права на использованные данные и материалы остаются за их правообладателями: расписание и сведения о занятиях — ДонГУ.