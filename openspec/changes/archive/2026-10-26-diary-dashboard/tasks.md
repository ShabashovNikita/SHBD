# Задачи: дашборд вкладки «Дневник»

## 1. Строки и маршруты

- [x] 1.1 Добавить строки en/ru: `diary_title` (Diary/Дневник), `diary_card_nutrition` (Nutrition/Питание), `diary_card_activity` (Activity/Активность), `diary_card_steps` (Steps/Шаги), `diary_card_water` (Water/Вода), `diary_card_sleep` (Sleep/Сон), `diary_card_weight` (Weight/Вес), `diary_meals_link` (Meals/Приёмы пищи), `diary_workouts_link` (Workouts/Тренировки), `diary_no_data` (No data added/Нет добавленных данных), `diary_connect_health` (Connect/Подключить), `diary_change_calorie_goal` (Change calorie goal/Изменить цель калорий), `diary_pick_date` (Pick a date/Выбрать дату), `diary_food_title` (Meals/Приёмы пищи), `diary_progress_title` (Progress/Прогресс), формат сна `diary_sleep_format` (%1$d h %2$d min / %1$d ч %2$d мин). Удалить `diary_segment_today/progress`. Проверка: `gradlew :app:assembleDebug` проходит.
- [x] 1.2 В `ShbdRoutes` добавить `DIARY_FOOD = "diary/food?add={add}"` и помощник `diaryFood(add: Boolean)`; `PROGRESS` остаётся в `GO_DESTINATIONS`. В `ShbdRoutesTest` проверить, что `diary/food` не входит в `bottomTabs`. Проверка: тест проходит.

## 2. Данные дашборда

- [x] 2.1 Создать `ui/diary/DiaryDashboardMath.kt` с чистыми функциями `latestWeightOnOrBefore(entries, date)`, `sleepHoursMinutes(minutes)`, `progressFraction(current, goal)` (0..1, цель 0 → 0) и юнит-тест `DiaryDashboardMathTest`. Проверка: тест проходит.
- [x] 2.2 Создать `ui/diary/DiaryDashboardViewModel.kt`: по выбранному дню читает `health.capabilities()`, `readDailyActivity(30)`, `readDailyWellness(30)`, подписан на `weightRepository.entries`; отдаёт `StateFlow<DashboardExtras>` (минуты тренировок, минуты сна, вес и дата, флаги `stepsGranted/sleepGranted`). Ошибки Health Connect → пустые значения, без падения. Проверка: сборка проходит.

## 3. Экраны

- [x] 3.1 Создать `ui/diary/DiaryDashboardCards.kt`: `DashboardCard` (иконка, заголовок, содержимое, необязательные «ссылка ›» и «+»), `WeekCard` (месяц и год, значок календаря + `DatePickerDialog`, `WeekEnergyStrip`), `NutritionCard` (ккал / цель, «⋮» с `DropdownMenu`, три полоски БЖУ), `ActivityCard`, `StepsCard`, `WaterCard`, `SleepCard`, `WeightCard`, пустое состояние «Нет добавленных данных». Добавить `@Preview` (светлая и тёмная тема). Проверка: сборка проходит, превью открываются в Android Studio.
- [x] 3.2 Создать `ui/diary/DiaryDashboardScreen.kt`: `LazyColumn` с отступом статус-бара и `BottomNavScrollPadding`; берёт `HomeViewModel` (выбранный день, калории, цель, БЖУ, вода, шаги, активные ккал) и `DiaryDashboardViewModel`. Колбэки: `onOpenMeals`, `onAddFood`, `onOpenProgress`, `onOpenGoals`, `onOpenHealthSettings`. Локальные листы: `ManualActiveSheet` («+» Активность → `addManualActive`), выбор порции воды («+» Вода → `addWater`), `AddWeightDialog` («+» Вес → `weightRepository.addEntry`). Проверка: сборка проходит.
- [x] 3.3 Создать `ui/diary/DiarySubScreens.kt`: `DiaryFoodScreen` (`TopAppBar` «Приёмы пищи» со стрелкой «Назад» + `HomeScreen`) и `DiaryProgressScreen` (то же для `ProgressScreen`). В `HomeScreen` добавить параметр `openAddFoodOnStart: Boolean = false`, который один раз открывает меню добавления еды. Проверка: сборка проходит.

## 4. Навигация

- [x] 4.1 В `ShbdNavHost`: `composable(HOME)` рисует `DiaryDashboardScreen`; добавить `composable(DIARY_FOOD)` с общим `HomeViewModel` через `LocalViewModelStoreOwner` записи HOME (запасной вариант — своя модель); вернуть `composable(PROGRESS)` → `DiaryProgressScreen`; удалить `diarySegment`, `DiaryScreen.kt`, `DiarySegment`. Проверка: сборка проходит.
- [x] 4.2 В `ShbdNavHost` перевести обработку `sharedImageInbox` и `shortcutEntryInbox` на переход к `DIARY_FOOD` (поверх HOME, `launchSingleTop`); `shbd://go/progress` → `PROGRESS` поверх HOME. Проверка: сборка проходит; ручная проверка в 6.3.

## 5. Тесты и сборка

- [x] 5.1 Обновить `ShbdRoutesTest` / `CoachTabToggleTest`, если затронуты; проверить, что `maestro/flows/progress-range-chips.yaml` открывает Прогресс через карточку (поправить шаги). Проверка: текст сценария соответствует новым подписям.
- [x] 5.2 Прогнать `gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:compileDebugScreenshotTestKotlin`. Проверка: всё проходит, 0 падений.

## 6. Ручная проверка в Android Studio (пользователь)

- [x] 6.1 Дневник: заголовок, карточка недели (месяц, календарь, 7 дней), карточки Питание → Тренировки → Вес → Шаги → Вода → Сон; светлая и тёмная тема; ничего не залезает под статус-бар и нижнее меню.
- [x] 6.2 Выбор вчерашнего дня и даты из календаря меняет данные всех карточек; «Приёмы пищи ›» открывает тот же день; «+» в Питании сразу открывает меню добавления; «⋮» → «Изменить цель калорий» → раздел «Цели»; «+» в Воде, Тренировках, Весе меняет карточки.
- [x] 6.3 Ссылки и входящие: `shbd://go/progress` → экран Прогресса, «Назад» → дашборд; поделиться фото из Галереи → «Приёмы пищи» с разбором; без Health Connect Шаги/Сон показывают «Нет добавленных данных» и «Подключить».

## 7. Документация

- [x] 7.1 Обновить журнал прогресса: что сделано, блок «Проверить / Обратить внимание» для `diary-dashboard`, напоминание для `diary-block-screens` (заменить временные экраны Главной и Прогресса). Проверка: файл обновлён.

## 8. Доработки по замечаниям (26.10.2026)

- [x] 8.1 `ManualDayMetricsRepository` + ключ `manualDayMetrics` + `AppContainer.manualDayMetricsRepository`. Проверка: сборка проходит.
- [x] 8.2 `ManualMetricDialogs.kt` (ввод шагов, ввод сна, «Брать данные из Health Connect»); кнопка ввода на карточках «Шаги» и «Сон»; показ `manual ?: Health Connect` с пометкой «Введено вручную». Строки en/ru. Проверка: сборка проходит.
- [x] 8.3 Карточка тренировок через общий `ActivityValues` (ккал тем же шрифтом, что в «Питании»). Прибавка тренировок к цели (миграция v3) отменена в 9.2. Проверка: юнит-тесты проходят.
- [x] 8.4 Компактные карточки (`DashboardCard`, `MacroColumn`, `WeekCard`, `ValueFontSize`) и `bottomNavContentPadding`. Проверка: сборка проходит.
- [x] 8.5 Ручная проверка (пользователь): без Health Connect ввести шаги и сон через «+», сменить день — значения свои у каждого дня; «Брать данные из Health Connect» возвращает данные HC; дашборд прокручивается до конца, последняя карточка не прячется под меню; иконки и шрифты меньше, чем раньше.

## 9. Вторая доработка по замечаниям (26.10.2026)

- [x] 9.1 Переименовать «Активность» в «Тренировки» (`diary_card_activity`), ссылка «Все тренировки» (`diary_workouts_all`); убрать строку «+N ккал к цели» и `diary_activity_goal_bonus`. Проверка: grep `diary_activity_goal_bonus` по `app/src` пуст.
- [x] 9.2 Цель фиксирована: дашборд и «Приёмы пищи» берут `baseCalorieGoal` и масштаб БЖУ `1f`; карточка тренировок — `liveActiveBurn`; миграция раскладки v4 → `STATIC`, без `fullCredit`. Проверка: сборка и юнит-тесты проходят.
- [x] 9.3 Порядок карточек: Питание, Тренировки, Вес, Шаги, Вода, Сон. Проверка: сборка проходит.
- [x] 9.4 `HeaderAddButton` («+») на «Воде», «Весе», «Шагах», «Сне»; удалить `ManualEditButton`. Проверка: grep `ManualEditButton` по `app/src` пуст.
- [x] 9.5 Ручная проверка (пользователь): записать тренировку 1000 ккал — в «Питании» цель не изменилась (например, 0 / 1 985), в «Тренировках» 1 000 ккал; «Вес» стоит перед «Шагами»; у «Шагов» и «Сна» в заголовке «+», как у «Воды» и «Веса».

## Workflow follow-up

- Архивировать после `main-navigation-4-tabs` (эта спека меняет её требования): `openspec archive diary-dashboard`.
