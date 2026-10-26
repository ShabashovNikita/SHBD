# Задачи: экраны блоков Дневника

## 1. Маршрут и нижнее меню

- [x] 1.1 Добавить `enum class DiaryBlock` (nutrition, activity, steps, water, sleep, weight; `fromId` → null для неизвестного) и в `ShbdRoutes`: `DIARY_BLOCK = "diary/block/{block}?add={add}"`, `diaryBlock(block, add)`. Удалить `DIARY_FOOD`/`diaryFood`. В `ShbdRoutesTest` заменить тест `diaryFood_*` на тесты: `diaryBlock` строит адрес для каждого блока, `fromId("x") == null`, `diary/block` не входит в `bottomTabs` и go-адреса. Проверка: `:app:testDebugUnitTest --tests "*ShbdRoutesTest*"` проходит.
- [x] 1.2 В `ShbdNavHost`: `showTabs` = вкладка **или** `DIARY_BLOCK`; в `BottomNavBar` передавать «маршрут вкладки» (`DIARY_BLOCK` → `HOME`), повторное нажатие «Дневник» на блоке → `popBackStack(HOME, false)`. Проверка: сборка проходит; поведение — в 7.1.

## 2. Каркас и графики

- [x] 2.1 Создать `ui/diary/blocks/DiaryChartMath.kt`: `DayValue(date, value: Double?)`, `daySeries(range, anchor, values)`, `bucketSeries(series, range)` (неделя/месяц — по дням, 3/6 мес — средние по неделям, год — по месяцам, пустые дни не участвуют), `hasAnyValue`. Юнит-тест `DiaryChartMathTest`: границы периода включают выбранный день, дни без данных = null, средние недели без пустых дней, год даёт 12–13 столбиков. Проверка: тест проходит.
- [x] 2.2 Создать `ui/diary/blocks/DiaryBarChart.kt`: столбики на `Canvas`, пунктир цели, подписи осей через `niceAxisTicks`/`pickXLabelIndices`/`formatTick`; если `!hasAnyValue` — `DashboardEmptyText`. `@Preview` светлый и тёмный. Проверка: `:app:compileDebugKotlin` проходит, превью рисуется в Android Studio.
- [x] 2.3 `TimeRangePicker` — параметр `options: List<TimeRange> = TimeRange.entries`; `DiaryChartRanges` = Неделя, Месяц, 3 мес, 6 мес, Год. `ProgressViewModel.setAnchorDate(date)`: диапазоны и `calorieSlots` считаются от `anchor` (по умолчанию сегодня). Добавить юнит-тест на диапазон с якорем в прошлом (рядом с существующими тестами `ProgressViewModel`/`TimeRange`). Проверка: `:app:testDebugUnitTest` — 0 падений.
- [x] 2.4 Создать `ui/diary/blocks/DiaryBlockScaffold.kt`: шапка (стрелка «Назад», заголовок, подзаголовок), `LazyColumn` с отступом статус-бара и `BottomNavScrollPadding`, слоты `summary`/`entries`/`chart`, FAB «+» над нижним меню (`BottomOverlayPadding`). `WeekCard` и `DashboardEmptyText` сделать `internal`. Секция графика: заголовок «Прогресс», `TimeRangePicker(DiaryChartRanges)`, содержимое. Строки en/ru: заголовки и подзаголовки 6 блоков, «Прогресс», «Замеры тела». Проверка: сборка проходит.

## 3. Данные блоков

- [x] 3.1 Создать `ui/diary/blocks/DiaryBlockViewModel(block)`: по `anchor` и `range` читает Health Connect (`readDailyActivity`/`readDailyWellness` на `n` дней до сегодня) или `waterRepository.entries`, отдаёт `series: List<DayValue>?` (null — загрузка), записи дня (порции воды) и флаги доступа Health Connect. Чистое преобразование «список дней Health Connect → Map<LocalDate, Double>» — функцией в `DiaryChartMath` с тестом. Проверка: `:app:testDebugUnitTest` проходит.

## 4. «Приёмы пищи»

- [x] 4.1 В `HomeScreen` добавить `blockMode: HomeBlockMode? = null` (слоты `header`, `emptyContent`, `footer`): при нём не рисуются `WeekEnergyStrip`, герой калорий, плитки БЖУ и заголовок «Еда за сегодня»; голодание/кофеин/никотин/заметка дня, список, FAB, листы, режим выбора и входящие работают как раньше; FAB и его отступ — как у каркаса. Без `blockMode` Главная не меняется. Проверка: сборка проходит; `ReleaseScreenshotPreviews` компилируются (`:app:compileDebugScreenshotTestKotlin`).
- [x] 4.2 Вынести общую часть `NutritionCard` (калории и БЖУ) в функцию; `NutritionSummaryCard` — без «⋮», ссылки и «+». Создать `NutritionBlockScreen`: `HomeScreen(blockMode = …)`, шапка «Приёмы пищи / Питание» + `WeekCard` + сводка, пустой список → «Нет добавленных данных», подвал — `CalorieSection`, `ProgressAdherenceSection`, `MacroAveragesSection`, `NutrientAveragesSection` из общего `ProgressViewModel` (`setAnchorDate(ui.date)`). Параметр `add=true` → `openAddFoodOnStart`. Проверка: сборка проходит.

## 5. Остальные блоки

- [x] 5.1 `ActivityBlockScreen`: сводка (минуты тренировок, активные ккал), список `manualActiveTodayEntries` с удалением, FAB → `ManualActiveSheet`, график минут тренировок, «Подключить» без доступа. Проверка: сборка проходит, `@Preview` светлый/тёмный.
- [x] 5.2 `StepsBlockScreen` (шаги/цель с полоской, график с целью) и `SleepBlockScreen` (часы и минуты сна, график часов), без FAB; без доступа — «Нет добавленных данных» + «Подключить» → `SETTINGS_HEALTH`. Проверка: сборка проходит, `@Preview`.
- [x] 5.3 `WaterBlockScreen`: сводка выпито/цель (мл или fl oz), список порций дня со временем, правка и удаление через существующие листы воды `HomeViewModel`, FAB → выбор объёма, график с линией цели. Проверка: сборка проходит, `@Preview`.
- [x] 5.4 `WeightBlockScreen`: сводка (последний вес на выбранный день, дата, % жира), `BodySection`, ссылки истории веса и % жира с их листами, `MeasurementPlotCard` для включённых замеров, ссылка «Замеры тела» → `BODY_MEASUREMENTS`; диалоги веса, % жира и замеров перенести из `ProgressScreen`; FAB → `AddWeightDialog`. Проверка: сборка проходит, `@Preview`.

## 6. Переходы и удаление временного

- [x] 6.1 `composable(DIARY_BLOCK)` в `ShbdNavHost`: `HomeViewModel` и `ProgressViewModel` из записи HOME через `LocalViewModelStoreOwner` (запасной вариант — своя запись), выбор экрана по `DiaryBlock`, неизвестный блок → назад на HOME. Переходы с дашборда — по таблице из design.md (карточки целиком, «Приёмы пищи ›», «Тренировки ›», «+» в Питании с `add=true`). `bringMealsForward()` → `diaryBlock(NUTRITION)`, проверка «уже открыт» по аргументу `block`. `shbd://go/progress` → `diaryBlock(WEIGHT)` поверх HOME; удалить `composable(PROGRESS)`. Проверка: сборка проходит.
- [x] 6.2 Удалить `DiarySubScreens.kt`, строки `diary_food_title`, `diary_progress_title`; у `ProgressScreen` пометить комментарием, что он остался только ради превью скриншот-тестов. Обновить Maestro-сценарии, которые открывали «Приёмы пищи»/«Прогресс» (`progress-range-chips`, `add-food-off-chip`, `fill-gaps`, `typed-amount-manual-entry`, `untracked-day-mark`, `ai-profile-custom-workflow`, `diary-import`) под новые заголовки. Проверка: `grep` по проекту не находит `DiaryFoodScreen`, `diaryFood(`, `DiaryProgressScreen`.
- [x] 6.3 Прогнать `gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:compileDebugScreenshotTestKotlin`. Проверка: всё проходит, 0 падений.
- [x] 6.4 Обновить журнал прогресса `C:\Users\Пользователь\Music\ПРОГРЕСС проекта SHBD.txt`: статус изменения, «сделано / доделать», блок «[4] diary-block-screens» с пунктами «Проверить» и «Обратить внимание» (история Health Connect 30 дней, режим блока у `HomeScreen`, общий период графиков). Проверка: файл обновлён.

## 7. Ручная проверка в Android Studio (пользователь)

- [x] 7.1 Каждая карточка дашборда открывает свой экран; заголовок и подзаголовок как в спеке; нижнее меню видно, выделен «Дневник»; «Назад» и повторное нажатие «Дневник» → дашборд; переход на «Профиль» и обратно без падений; светлая и тёмная тема.
- [x] 7.2 «Приёмы пищи»: вид как в макете (описан в proposal); пустой день — «Нет добавленных данных»; «+» (и «+» с дашборда) открывает меню добавления; правка, удаление, выбор нескольких, фото, штрихкод работают; FAB не висит слишком высоко, внизу нет лишней пустоты; внизу «Дефицит калорий» (с раздела 11) и соблюдение плана, карточки «Средние макросы» нет.
- [x] 7.3 Смена дня на экране блока меняется и на дашборде; переключатель периода меняет график; период заканчивается выбранным днём; Вода (добавить/удалить порцию), Тренировки (ручная запись), Вес (запись, история, замеры тела), Шаги и Сон без Health Connect — «Подключить»; `shbd://go/progress` → «Вес»; фото из Галереи → «Приёмы пищи» с разбором.

## 8. Доработки по замечаниям (26.10.2026)

- [x] 8.1 «Шаги» и «Сон»: FAB «+» → `ManualMetricEditors`; сводка `StepsValues`/`SleepValues`; график с `withOverrides` + тест в `DiaryChartMathTest`. Проверка: тест проходит.
- [x] 8.2 «Тренировки»: сводка `ActivityValues` (шрифт как у калорий). Проверка: сборка проходит.
- [x] 8.3 Убрать `MacroAveragesSection` с «Приёмов пищи». Проверка: grep `MacroAveragesSection` в `ui/diary` пуст.
- [x] 8.4 Подписи дат: `LocaleFormat.axisDay()` + тест в `LocaleFormatTest`; `TrendXAxisLabels`, график калорий и `DiaryBarChart` используют его. Проверка: тест проходит.
- [x] 8.5 «Больше о питании» в конец четырёх форм блюда. Проверка: сборка проходит.
- [x] 8.6 Отступ снизу экранов блоков — `bottomNavContentPadding`. Проверка: сборка проходит.
- [x] 8.7 Ручная проверка (пользователь): «Шаги» и «Сон» — «+» сохраняет значение, оно видно в сводке и на графике; «Тренировки» — шрифт ккал как у калорий, цель калорий от тренировок не растёт; на «Приёмах пищи» нет «Средних макросов»; на «Весе» и «Приёмах пищи» при «Неделя» и «Месяц» даты под графиком не наезжают; в ручном вводе блюда «Больше о питании» — последней строкой над «Сохранить», в правке записи — в самом низу; внизу экранов блоков ничего не прячется под меню.

## 9. Вторая доработка по замечаниям (26.10.2026)

- [x] 9.1 `deficitByDay()` и `workoutsByDay()` в `DiaryChartMath.kt` + тесты в `DiaryChartMathTest`. Проверка: тесты проходят.
- [x] 9.2 `DiaryBarChart`: отрицательные значения (нулевая линия, `negativeColor`); `formatTick` для отрицательных. Проверка: сборка и юнит-тесты проходят.
- [x] 9.3 `CalorieDeficitCard` на экране «Вес» после графика веса; строки en/ru. Проверка: сборка проходит.
- [x] 9.4 Подзаголовок «Активность» у «Тренировок» и «Шагов» — отдельная строка `diary_block_subtitle_activity` (карточка теперь называется «Тренировки»). Проверка: сборка проходит.
- [x] 9.5 Ручная проверка (пользователь; с 11-го раздела график дефицита — в «Приёмах пищи»): на «Весе» два графика — вес и «Дефицит калорий»; записать еду и тренировку за сегодня — точка = норма − съедено + тренировка; день переедания — ниже нуля; дни без еды пустые; под графиком итог в ккал и кг; переключатель периода меняет оба графика.

## 10. Третья доработка по замечаниям (26.10.2026)

- [x] 10.1 График дефицита — линейный (`MeasurementChartCanvas`, параметр `lineColor`), без пояснения. Проверка: сборка проходит.
- [x] 10.2 Ось X за весь период: `periodDomainMs()`, параметр `xDomain` в `buildOverlayChartModel`, `buildMeasurementChartModel`, `BodySection`, `MeasurementPlotCard`. Проверка: `OverlayModelTest`, `DiaryChartMathTest` проходят.
- [x] 10.3 Убрать переключатели единиц из диалога веса, диалога замеров и листов «Рост»/«Вес» Профиля. Проверка: grep `UnitToggle(` по `app/src/main/java` находит только сам компонент.
- [x] 10.4 Ручная проверка (пользователь): «Вес» → «+» — колесо в кг без «lbs»; замер тела — в см без «in»; при одной-двух записях «Неделя» → «Месяц» меняет ось X и подписи дат у графиков веса, дефицита и замеров; график дефицита — линия, без пояснения, нажатие на точку показывает дату и ккал.

## 11. Четвёртая доработка по замечаниям (26.10.2026)

- [x] 11.1 «Дефицит калорий» — в «Приёмах пищи» вместо карточки «Прогресс» с `CalorieSection` (`CalorieDeficitChart` в `BlockChartCard` с заголовком); с «Веса» убран. Проверка: сборка проходит.
- [x] 11.2 `BodySection`: параметры `title` («Вес»), `weightLocked` (вес всегда виден, кнопки веса нет), `rangePicker`; у `MeasurementPlotCard` — `rangePicker`; общий `DiaryRangePicker`. Проверка: сборка проходит.
- [x] 11.3 Окно графика от первой записи: `ChartPeriod`, `chartWindow()`, `domainMs()`, `tickDays()` в `ChartPeriod.kt`; `daySeries()` и модели линейных графиков берут окно оттуда; параметр `xDomain` и `periodDomainMs()` заменены на `period`. Проверка: `DiaryChartMathTest`, `OverlayModelTest` проходят.
- [x] 11.4 Подписи оси X: `axisTickOffsets()` (неделя — все дни, месяц — 1/7/14/21/последний, иначе 5), `chartTickLabel()`; `DiaryBarChart` и `PeriodXAxisLabels` в `TrendXAxisLabels`. Проверка: `DiaryChartMathTest.chartWindow_andAxisTicks` проходит.
- [x] 11.5 Период при запуске — из настройки (заводская «Неделя»), без последнего просмотренного (`ProgressViewModel`). Проверка: сборка проходит.
- [x] 11.6 Ручная проверка (пользователь): «Приёмы пищи» — внизу «Дефицит калорий» с переключателем периода, столбиков калорий нет; «Вес» — карточка «Вес» без кнопки веса, дефицита нет, у замеров свой переключатель; одна запись веса сегодня — точка у левого края при «Неделе» и «Месяце»; подписи: неделя — 7 дней недели, месяц — 5 дат; после перезапуска приложения выбрана «Неделя».

## Workflow follow-up

- Архивировать после `main-navigation-4-tabs` и `diary-dashboard`: эта спека меняет их требования (`openspec archive diary-block-screens`).
