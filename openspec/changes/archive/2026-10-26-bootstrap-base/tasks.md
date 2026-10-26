# Задачи

## 1. Подготовка репозитория

- [x] 1.1 Создать корневой `.gitignore` (секреты, сборка, IDE — см. design, решение 9); проверка: `git check-ignore -v secrets.properties keystore.properties x.jks .idea/ .qwen/ app/build/` выводит правило для каждого пути
- [x] 1.2 Скопировать Android-проект основы в корень репозитория (без `build`, `.gradle`, `.idea`, `.kotlin`, `local.properties`, `secrets.properties*`, `keystore.properties`), а также `contracts/` и `testdata/parity/`; проверка: размер Android-проекта (`app/`, `gradle/`) ≈ 25 МБ, `git status` не показывает игнорируемых файлов

## 2. Смена пакета и имени

- [x] 2.1 Перенести каталоги исходного пакета → `app/shbd` во всех source set'ах (включая `screenshotTestDebug/reference`); проверка: в `app/src` нет каталогов старого пакета
- [x] 2.2 Заменить имя исходного пакета → `app.shbd` в `.kt/.kts/.xml/.pro/.json`; проверка: grep по старому имени пакета в `app` и `*.kts` пуст
- [x] 2.3 `build.gradle.kts`: `versionCode = 1`, `versionName = "0.1.0"`; свойство `shbd.parity.dir` (+ `ParityFixtures.kt`); `settings.gradle.kts`: `rootProject.name = "SHBD"`; run-конфигурация `.run/SHBD.run.xml`; проверка: grep по старому имени свойства пуст
- [x] 2.4 `app_name` и все пользовательские вхождения старого имени приложения в `res/values*/strings.xml` → «SHBD»; проверка: grep по старому имени в `app/src/main/res` пуст
- [x] 2.5 Переименовать классы и файлы с префиксом исходного проекта в `Shbd*` (решение 3), включая `android:name` класса `Application` в манифесте и тесты; проверка: сборка и юнит-тесты проходят, grep по старому префиксу с заглавной буквой после него в `app/src` пуст

## 3. Deep link, обновления, внешние ссылки

- [x] 3.1 Манифест: один intent-filter `shbd://add-meal`, схемы исходного приложения удалены; `MealShare.kt`: исходящая и входящая схема `shbd`; `MainActivity`/`ShbdLaunchIntents`: `shbd://go/<dest>`; обновить юнит-тесты MealShare; проверка: тесты `MealShare*` проходят
- [x] 3.2 Добавить `UpdateCheckFeature.ENABLED = false` и закрыть им все вызовы `AndroidUpdateChecker` и UI обновлений; проверка: grep вызовов показывает гард, юнит-тесты update проходят
- [x] 3.3 Заменить в коде ссылки на репозиторий и сайт исходного проекта, видимые пользователю или уходящие в сеть (User-Agent OFF, HTTP-Referer, `PRIVACY_POLICY_URL`, `about_share_message` во всех локалях); проверка: grep по их адресам в `app/src/main` (`*.kt`, `*.xml`) находит только комментарии

## 4. Сборка и проверка

- [x] 4.1 `gradlew.bat :app:assembleDebug` проходит без `secrets.properties`
- [x] 4.2 `gradlew.bat :app:testDebugUnitTest` проходит (падения, вызванные переименованием, исправлены; прочие — зафиксированы в журнале прогресса)
- [x] 4.3 Итоговый grep по старому имени (без учёта регистра) по Android-проекту (`app/`, `*.kts`): остаются только комментарии и проверки совместимости форматов импорта
- [x] 4.4 Обновить `C:\Users\Пользователь\Music\ПРОГРЕСС проекта SHBD.txt` и инструкцию по сборке (имя конфигурации запуска `SHBD`, пакет `app.shbd.debug`)

## 5. Ручная проверка пользователем

- [x] 5.1 Пользователь открывает корень репозитория SHBD в Android Studio, запускает на эмуляторе: в лаунчере «SHBD», открывается онбординг, затем главный экран

## Workflow follow-up

- Пользователь проверяет и сам делает коммит.
- После проверки — `openspec archive bootstrap-base`.
