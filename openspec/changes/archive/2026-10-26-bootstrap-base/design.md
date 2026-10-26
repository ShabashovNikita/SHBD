# Дизайн: основа приложения и смена идентичности

## Контекст

- Исходник: open-source Android-счётчик калорий (MIT). Один модуль `:app`.
- **Стек:** Kotlin 2.2, Jetpack Compose (Material 3), Navigation Compose, AGP 9.2,
  Gradle 9.4.1, JDK 17, `minSdk 26`, `targetSdk 36`.
- **Библиотеки:** DataStore Preferences, security-crypto (EncryptedSharedPreferences +
  Android Keystore для ключей API), Health Connect, WorkManager, Glance (виджеты), CameraX,
  zxing-cpp (штрихкоды), OkHttp, kotlinx.serialization, coroutines, Coil, LiteRT-LM
  (модель Gemma на устройстве).
- **Архитектура:**
  - ручной DI без Hilt: класс `Application` создаёт `AppContainer` со всеми репозиториями и
    сервисами, ViewModel получают его через вложенный `Factory(container)`;
  - данные пользователя — в DataStore Preferences (`data/PreferencesStore*.kt`, ключи в
    `PreferencesKeys.kt`) и в JSON-файлах по месяцам (`JsonBucketStore`); SQLite только для
    read-only баз продуктов USDA/Swiss в `assets/`;
  - состояние экранов — `StateFlow` в ViewModel; чистая логика и формулы — в `models/`
    без зависимостей от Android (покрыты юнит-тестами).
- Исходники ~24 МБ, `app/build` ~380 МБ (не переносим).
- Исходный пакет встречается примерно в 670 файлах в 8 source set'ах, включая эталонные
  картинки скриншот-тестов `screenshotTestDebug/reference/...`.
- Юнит-тесты читают `testdata/parity` из корня репозитория (путь передаётся системным
  свойством), а `ContractSchemaPresenceTest` — ещё и `contracts/`.
- В репозитории SHBD уже есть `README.md`, `openspec/`, `.idea/`, `.qwen/`, `CLAUDE.md`; `.gitignore` нет.
- На машине нет Python, поэтому скрипт генерации иконок запустить нельзя.

## Цели / Не цели

**Цели:** собираемая копия с новым пакетом и именем; поведение основы сохраняется 1:1;
ни одного секрета в git.

**Не цели:** редизайн, новые вкладки, своя модель ИИ, бэкенд — всё это следующие изменения.
Новая иконка и лицензии — отложены (см. «Открытые вопросы»).

## Решения

1. **Копирование файлов, а не git-слияние** (требование пользователя). Используем `robocopy` с
   исключениями: `build`, `.gradle`, `.idea`, `.kotlin`, `local.properties`, `secrets.properties*`,
   `keystore.properties`. Чужую историю не тянем — первый коммит сделает пользователь.

2. **Переименование пакета скриптом**:
   - перенести каталоги исходного пакета → `…/app/shbd/` во всех source set'ах;
   - заменить имя исходного пакета → `app.shbd` во всех `.kt`, `.kts`, `.xml`, `.pro`, `.json`.
   - *Альтернатива:* Refactor → Rename в Android Studio. Отклонено: не трогает эталонные PNG
     скриншот-тестов, gradle-файлы и строки в манифесте, к тому же это ручная работа.

3. **Имя приложения в пользовательских строках** → «SHBD» во всех `res/values*/strings.xml`.
   Внутренние имена классов с префиксом исходного проекта переименованы механической заменой
   префикса на `Shbd` (`ShbdApp`, `ShbdRoutes`, `ShbdNavHost`, `ShbdBottomNavBar`, `ShbdTheme`,
   `ShbdDialog`, `ShbdSurface`…), файлы — так же (`ShbdApp.kt`, `ShbdRoutes.kt`…). Строковые
   литералы совместимости форматов импорта (решение 7) при этом не меняются.

4. **Deep link**: в `AndroidManifest.xml` остаётся один intent-filter `shbd://add-meal`; фильтры
   схем исходного приложения и его предшественников удаляются. В `MealShare.kt` исходящая схема →
   `shbd`; разбор устаревших схем убирается. Ссылки из уведомлений (`MainActivity`,
   `ShbdLaunchIntents`) → `shbd://go/<dest>`.

5. **Проверка обновлений**: вызовы `AndroidUpdateChecker` выключаются флагом
   `UpdateCheckFeature.ENABLED = false` (по образцу уже существующего флага `FillGapsFeature`):
   код остаётся, сети нет, UI «есть обновление» не показывается. *Альтернатива:* удалить пакет
   `services/update/` — отклонено, больше правок в `AboutScreen`/`NavHost`/`NotificationService`
   без выигрыша.

6. **Ссылки и заголовки**: `HTTP-Referer` (OpenRouter) и User-Agent Open Food Facts → `SHBD/Android/<версия>`;
   `PRIVACY_POLICY_URL` и текст «поделиться приложением» — нейтральные, без ссылок на репозиторий
   исходного проекта (политику конфиденциальности SHBD добавим вместе с бэкендом, пока ссылка ведёт
   на заглушку в README).

7. **Хранилище**: имена файлов `fudai_prefs`, `fudai_keychain`, префикс Health Connect `fudai_` не
   меняются: без миграции их менять нельзя, а у нового пакета хранилище и так пустое.
   Импорт файлов дневника и синхронизации продолжает принимать файлы, созданные исходным
   приложением (поле `app` с его именем), — это совместимость форматов.

8. **Версия**: `versionCode = 1`, `versionName = "0.1.0"` — это новое приложение.
   `rootProject.name = "SHBD"`; run-конфигурация `.run/SHBD.run.xml`
   (запуск `app.shbd.MainActivity` — из-за alias'ов иконок в манифесте).

9. **`.gitignore`** в корне — правила Android-проекта (сборка, Gradle, IDE, секреты) плюс
   `.qwen/`, `.claude/`, `.env*`, `*.aab`.

10. **Свойство тестов** с путём к эталонам → `shbd.parity.dir` (в `build.gradle.kts` и `ParityFixtures.kt`).

11. **Android-проект — в корне репозитория**, без подкаталога `android/`: в SHBD только
    Android-приложение (PWA, сайт и прочие части исходного репозитория не переносятся).
    Gradle-проект (`settings.gradle.kts`, `app/`, `gradle/`, `gradlew*`) лежит рядом с
    `contracts/`, `testdata/` и `openspec/`; путь к эталонам в `app/build.gradle.kts` —
    `testdata/parity` от корня. В Android Studio открывается корень репозитория.

## Риски / компромиссы

- [Массовая замена задела строки, которые должны остаться прежними: проверки поля `app` в
  импортёрах] → заменяем только имя пакета (с точкой), префикс имён классов (заглавная буква
  после префикса) и имя приложения в ресурсах; строчные литералы в импортёрах не трогаем;
  финальный grep показывает остаток.
- [Скриншот-тесты сломаются из-за смены пути пакета или имени] → переносим эталоны вместе с
  пакетом; если отличаются из-за текста «SHBD», перегенерируем эталоны (`updateDebugScreenshotTest`).
- [Gradle sync упадёт из-за `local.properties` с чужим путём SDK] → `local.properties` не копируем;
  Android Studio создаст его сама.
- [Остаются иконки основы] → осознанный компромисс до получения исходника иконки.

## Открытые вопросы

- Исходник иконки SHBD (SVG/PNG 1024×1024) — пользователь даст позже; тогда же поставим Python и
  перегенерируем иконки скриптом.
- Лицензии/NOTICE (MIT требует сохранить текст лицензии и указание происхождения) — в конце
  проекта, по решению пользователя.
