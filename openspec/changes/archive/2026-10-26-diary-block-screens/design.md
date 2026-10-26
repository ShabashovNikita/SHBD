# Дизайн: экраны блоков Дневника

## Контекст

Что есть после `diary-dashboard` (папка `app/src/main/java/app/shbd/`):

- **Дашборд**:
  - `ui/diary/DiaryDashboardScreen.kt`, `DiaryDashboardCards.kt`: `WeekCard`, `NutritionCard`,
    `DashboardCard`, `DashboardEmptyText` и другие;
  - `DiaryDashboardViewModel` (сон, тренировки, вес);
  - `DiaryDashboardMath.kt`.
  Выбранный день хранит `HomeViewModel` из записи навигации HOME.
- **Временные экраны** `ui/diary/DiarySubScreens.kt`:
  - `DiaryFoodScreen` рисует `HomeScreen` целиком: свою неделю, героя калорий, список еды,
    FAB и все листы;
  - `DiaryProgressScreen` рисует `ProgressScreen`.
  Оба экрана не входят в `ShbdRoutes.bottomTabs`, поэтому нижнее меню на них скрыто.
- **`HomeScreen`** (2462 строки) — `Scaffold`, внутри `LazyColumn`:
  - `WeekEnergyStrip`;
  - блок с героем калорий, БЖУ, голоданием, кофеином и никотином (около строк 584–850);
  - заметка дня;
  - заголовок «Еда за сегодня» и группы приёмов пищи;
  - FAB «+» и около 40 листов: добавление еды, камера, правка и т. д.
  Листы и обработка входящих фото привязаны к этому экрану. Вынести их отдельно — большая
  переделка.
- **Экран прогресса основы** `ui/progress/`:
  - `ProgressViewModel` с `TimeRange` (WEEK…ALL_TIME, `dateRange(today)`), `activity`,
    `wellness`;
  - секции `BodySection`, `MeasurementPlotCard`, `CalorieSection` (`CalorieBarChart`),
    `ProgressAdherenceSection`, `MacroAveragesSection`, `NutrientAveragesSection`;
  - `TimeRangePicker`, `CardSection`, `ChartPlaceholder`, `DeferredChart`;
  - диалоги веса и % жира, листы истории.
- **Данные**:
  - вода — `container.waterRepository.entries` (все порции с датами);
  - ручная активность — `HomeViewModel.manualActiveTodayEntries` и `manualActiveByDay`;
  - шаги и тренировки — `health.readDailyActivity(days)`, сон — `readDailyWellness(days)`.
    Оба метода читают N дней назад от сегодня.

## Цели / Не цели

**Цели:**
- 6 экранов блоков по макету из ТЗ с общим каркасом и графиком за период.
- Не потерять ни одной функции прежних экранов «Главная» и «Прогресс».
- Повторно использовать готовые секции и расчёты, ничего не считать заново.

**Не цели:**
- Переписывать листы добавления еды.
- Новый движок графиков.
- Градиентный фон.
- Чтение истории Health Connect старше 30 дней.

## Решения

### 1. Один маршрут для всех блоков

`ShbdRoutes.DIARY_BLOCK = "diary/block/{block}?add={add}"`. Построитель
`diaryBlock(block: DiaryBlock, add: Boolean = false)`. Список блоков —
`enum class DiaryBlock(val id)`: `NUTRITION("nutrition")`, `ACTIVITY`, `STEPS`, `WATER`,
`SLEEP`, `WEIGHT`. Неизвестный `id` открывает дашборд. `DIARY_FOOD` и `composable(PROGRESS)`
удаляются. Константа `PROGRESS` остаётся только как адрес `go/progress`.

Почему один маршрут: одинаковый каркас, проще правило «нижнее меню видно» и один
`composable`.

### 2. Нижнее меню на экранах блоков

В `ShbdNavHost` правило `showTabs` становится таким: маршрут во вкладках **или** маршрут
`DIARY_BLOCK`. `BottomNavBar` получает «маршрут вкладки»: для `DIARY_BLOCK` это `HOME`, тогда
«Дневник» выделен. Нажатие на вкладку «Дневник» на экране блока делает `popBackStack(HOME)`.
На другие вкладки переход идёт как обычно, с `saveState`.

Отступы: экраны блоков, как и дашборд, добавляют `BottomNavScrollPadding` снизу, а FAB
поднимается над меню на `BottomOverlayPadding`. Так FAB стоит там же, где на образце.

### 3. Общий каркас `DiaryBlockScaffold`

Файл `ui/diary/blocks/DiaryBlockScaffold.kt`:
- шапка: стрелка «Назад» слева, по центру заголовок (`titleLarge`) и подзаголовок
  (`bodyMedium`, приглушённый);
- `LazyColumn` с отступом статус-бара. Порядок элементов:
  - `WeekCard` с дашборда, выбранный день из `HomeViewModel`;
  - `summary()`;
  - `entries()`;
  - `chart()`;
- необязательный FAB «+».

`WeekCard` и `DashboardEmptyText` становятся `internal` и переиспользуются.

`HomeViewModel` для всех блоков берётся из записи HOME через `LocalViewModelStoreOwner`, как
сейчас у `diary/food`. Отсюда общий выбранный день (требование «Выбранный день общий»).

### 4. «Приёмы пищи» — режим `HomeScreen`, а не новый экран

Чтобы сохранить все листы и функции, `HomeScreen` получает параметр
`blockMode: HomeBlockMode?`. Это класс с тремя слотами:
- `header: LazyListScope.() -> Unit` — шапка, неделя и сводка;
- `emptyContent` — «Нет добавленных данных», если еды нет;
- `footer: LazyListScope.() -> Unit` — график и средние.

Если `blockMode != null`:
- не рисуются `WeekEnergyStrip`, герой калорий и плитки БЖУ (их заменяет сводка из слота);
- заголовок «Еда за сегодня» не рисуется: секцию называет шапка блока;
- голодание, кофеин, никотин и заметка дня остаются, если включены в настройках. Это функции
  Главной, их нельзя потерять. Порядок: сводка → эти строки → список еды;
- FAB, листы, режим выбора, входящие фото и ярлыки работают как раньше;
- отступ статус-бара даёт шапка. Двойной отступ из прошлого изменения исчезает, потому что
  обёртки `DiarySubScreenFrame` больше нет.

Сводка — `NutritionSummaryCard`, это `NutritionCard` без «⋮», ссылки и «+». Общая часть
выносится в одну функцию, чтобы карточка дашборда и сводка не разошлись.

Подвал «Питания»:
- `CalorieSection` (график калорий по дням с целью; с решения 15 заменён графиком дефицита);
- `ProgressAdherenceSection`;
- `NutrientAveragesSection` (если включены дополнительные нутриенты). `MacroAveragesSection`
  («Средние макросы») на экране не показывается — доработка 26.10.2026.
Данные — из `ProgressViewModel`.

Альтернатива — новый экран, а листы добавления вынести в общий компонент. Отвергнута на этом
этапе: это переделка 2400 строк с высоким риском регрессий. Можно сделать позже отдельным
изменением.

### 5. Период графика заканчивается выбранным днём

`ProgressViewModel` получает `setAnchorDate(date)`. Расчёт диапазона и `calorieSlots`
используют `timeRange.dateRange(anchor)` вместо `LocalDate.now()`. На прежнем Прогрессе
`anchor = today`, поведение то же. Экраны блоков вызывают `setAnchorDate(ui.date)` при смене
дня. Список периодов на экранах блоков — `DiaryChartRanges = WEEK, MONTH, THREE_MONTHS,
SIX_MONTHS, YEAR`, без «Всё время». `TimeRangePicker` получает параметр `options`.

`ProgressViewModel` берётся из записи HOME (тот же `LocalViewModelStoreOwner`). Значит, один
экземпляр на все блоки, и выбранный период общий для всех блоков.

Тонкость: `ProgressViewModel` сохраняет выбранный период в настройках (`storageId`). Пусть
так и будет: пользователь вернётся к тому же периоду.

### 6. Графики для Шагов, Воды, Сна, Тренировок

Новый компонент `DiaryBarChart(points: List<DayValue>, goal: Double?, unit, color)` в
`ui/diary/blocks/DiaryBarChart.kt`. Он рисует столбики на `Canvas`, пунктирную линию цели и
подписи осей. Для подписей используются `niceAxisTicks`, `pickXLabelIndices`, `formatTick` из
`ProgressCharts.kt`, они уже `internal`. Стиль повторяет `CalorieBarChart`.

Чистые функции `ui/diary/blocks/DiaryChartMath.kt` (с юнит-тестами):
- `daySeries(range, anchor, values: Map<LocalDate, Double>)` → по точке на каждый день,
  `null` там, где данных нет;
- `bucketSeries(series, range)` — для «3 мес», «6 мес» и «Год» среднее по неделям или
  месяцам, иначе 90–365 столбиков не помещаются. Пустые дни в среднем не участвуют;
- `hasAnyValue(series)` — показывать график или «Нет добавленных данных».

Источники:

| Блок       | Значение по дням                            | Цель                        |
|------------|---------------------------------------------|-----------------------------|
| Шаги       | `readDailyActivity(n).steps`                | `homeDisplay.stepGoal`      |
| Тренировки | `readDailyActivity(n).exerciseMinutes`      | —                           |
| Сон        | `readDailyWellness(n).sleepMinutes / 60`    | —                           |
| Вода       | `waterRepository.entries`, сумма по дню     | `waterDailyGoalMl`          |

`n` — число дней от начала периода до сегодня. Если выбранный день в прошлом, это больше
длины периода. Чтение идёт в новой `DiaryBlockViewModel(block)`. Она берёт `anchor` и
`range` и отдаёт `List<DayValue>`, а также записи дня: порции воды и ручную активность.
`DiaryDashboardViewModel` остаётся только для дашборда.

### 7. «Вес»

Сводка: `latestWeightOnOrBefore` (уже есть) плюс последний % жира из `bodyFatRepository`.
Ниже — `BodySection`, `WeightHistoryLink`, `BodyFatHistoryLink`, `MeasurementPlotCard` для
включённых замеров и ссылка «Замеры тела» → `ShbdRoutes.BODY_MEASUREMENTS`. Диалоги
(`AddWeightDialog`, % жира, замер) и листы истории переносятся из `ProgressScreen` в
`WeightBlockScreen` вместе с их состоянием. FAB «+» → `AddWeightDialog`.

### 8. Переходы

| Откуда (дашборд)                    | Куда                                                       |
|-------------------------------------|------------------------------------------------------------|
| Карточка «Питание», «Приёмы пищи ›» | `diaryBlock(NUTRITION)`                                    |
| «+» в «Питании»                     | `diaryBlock(NUTRITION, add = true)`                        |
| Карточка «Тренировки», «Все тренировки ›» | `diaryBlock(ACTIVITY)`; «+» по-прежнему открывает лист   |
| Карточки «Шаги», «Сон», «Вода», «Вес» | свой блок; «+» и «Подключить» работают как раньше        |
| `shbd://go/progress`                | `diaryBlock(WEIGHT)` поверх HOME                           |
| Фото «Поделиться», ярлыки           | `bringMealsForward()` → `diaryBlock(NUTRITION)`            |

Проверка «уже на экране» в `bringMealsForward` смотрит на `DIARY_BLOCK` с
`block = nutrition`.

### 9. Удаление временного

- Удаляются `ui/diary/DiarySubScreens.kt`, строки `diary_food_title`, `diary_progress_title`,
  маршрут `DIARY_FOOD` и его тест.
- `ProgressScreen.kt` остаётся: его `ProgressScreenPreviewContent` рисуют скриншот-тесты.
  Сам `ProgressScreen` больше нигде не вызывается. Помечается комментарием: удалить, когда
  обновим эталоны скриншотов.
- Maestro-сценарии, которые ходили в «Приёмы пищи» или «Прогресс», правятся под новые
  заголовки.

### 10. Ручные шаги и сон на экранах блоков (доработка 26.10.2026)

`StepsBlockScreen` и `SleepBlockScreen` (общий `HealthBlockScreen`) получили FAB «+», который
открывает `ManualMetricEditors` из дашборда. Сводка — общие `StepsValues` / `SleepValues`
(правило `manual ?: Health Connect`). На графике ручные дни заменяют дни Health Connect: чистая
функция `withOverrides(health, manual)` в `DiaryChartMath.kt` (с юнит-тестом), сон переводится в
часы. Сводка «Тренировок» — общий `ActivityValues` с дашборда.

### 13. График дефицита калорий на «Весе» (вторая доработка 26.10.2026)

- Данные (всё уже есть в общих моделях): съедено по дням — `ProgressUiState.calorieSlots` (тот же
  период, что у графиков, дни «не отслеживается» исключены); тренировки — `HomeUiState`
  `healthEnergyActiveByDay` (ккал Health Connect по дням, хранится до 60 дней) + `manualActiveByDay`,
  сложение — `workoutsByDay()`; норма — `UserProfile.tdee` текущего профиля.
- Расчёт — чистая функция `deficitByDay(norm, eaten, workouts)` в `DiaryChartMath.kt` (юнит-тесты в
  `DiaryChartMathTest`): `norm − eaten + workouts` только для дней с едой > 0.
- Отрисовка — `CalorieDeficitCard` в `WeightBlockScreen` после `BodySection`: точки
  `TrendPoint(день, дефицит)` → `MeasurementChartCanvas` (тот же линейный график, что у замеров
  тела: тренд, точки, подсказка по нажатию), цвет линии — новый параметр `lineColor`
  (`colorScheme.primary`). Пояснения под заголовком нет (строка `diary_deficit_chart_hint` удалена).
  Диапазон — общий переключатель периода над графиком веса.
- `DiaryBarChart` умеет отрицательные значения (нулевая линия, `negativeColor`) — осталось от
  первого варианта графика дефицита; для шагов, воды, сна и тренировок вид не меняется.
  `formatTick` группирует разряды и для отрицательных чисел.
- Итог за период — сумма дней с данными; вес — сумма ÷ 7 700 ккал/кг.

### 11. Подписи дат на графиках

Причина наезда: подписи `MMM d` по-русски («окт. 25») шире места, рассчитанного под английские.
- `LocaleFormat.axisDay()` — короткая дата для осей: `dd.MM` («25.10»), для английского `M/d`.
- `TrendXAxisLabels` (вес, % жира, замеры): `BoxWithConstraints`, число подписей = ширина / 52 dp
  (от 2 до 5), каждая подпись — `weight(1f)`, одна строка без переноса; крайние прижаты к краям.
- График калорий: подписи `axisDay()`, минимальная ширина подписи 44 dp.
- `DiaryBarChart`: дни тоже через `axisDay()`.

### 12. «Больше о питании» внизу формы блюда

Блок раздела (строка с шевроном + карточка с нутриентами `MicronutrientField.MoreNutrition`)
перенесён без изменения логики в конец формы: `EditFoodEntrySheet` и `EditFavoriteSheet` —
последний элемент `LazyColumn`; `FoodResultSheet` — после «Что если» и информации о продукте;
ручной ввод (`HomeDialogs`) — после порции, над кнопкой «Сохранить».

### 14. Ось X за весь период и единицы (третья доработка 26.10.2026)

- **Ось X.** Раньше ось графиков веса и замеров шла от первой до последней записи, поэтому при паре
  записей «Неделя» и «Месяц» выглядели одинаково. Теперь `periodDomainMs(range, день)` в
  `DiaryChartMath.kt` даёт начало первого и конец последнего дня периода (для «Всего времени» —
  null, ось по данным). Этот интервал передаётся как `xDomain` в `BodySection` →
  `buildOverlayChartModel`, в `MeasurementPlotCard` → `MeasurementChartCanvas` →
  `buildMeasurementChartModel` и в график дефицита: ось = объединение периода и данных. Подписи
  дат (`TrendXAxisLabels`) и полоса маркеров дней строятся по той же оси. Тесты —
  `OverlayModelTest`, `DiaryChartMathTest.periodDomain…`.
- **Единицы.** Переключатели `UnitToggle` убраны из `AddWeightDialog`,
  `BodyMeasurementEntryDialog` и листов `HeightSheet` / `WeightSheet` Профиля, вместе с
  параметром `onUnitChange` у всех вызовов. Единицы задаются только в Профиле → Личные данные →
  Единицы; показ в фунтах/дюймах остаётся, если пользователь выбрал их там.

### 15. Окно графика от первой записи, дефицит в «Приёмах пищи» (четвёртая доработка 26.10.2026)

- **Окно.** `ChartPeriod(range, день)` и `chartWindow(range, день, firstDataDay)` в
  `ui/progress/ChartPeriod.kt`: окно всегда длиной `range.days`; если первая запись внутри
  периода позже его первого дня, окно начинается с неё и идёт вперёд, иначе заканчивается
  выбранным днём. Первая запись берётся из данных самого графика в пределах периода (данные
  графиков уже отфильтрованы по периоду), поэтому правило «первая запись слева» выполняется
  всегда. `daySeries()` строит столбики по этому окну; `buildOverlayChartModel` и
  `buildMeasurementChartModel` принимают `period` вместо `xDomain` и считают ось через
  `ChartPeriod.domainMs()`. `periodDomainMs()` из решения 14 удалена.
- **Подписи.** `axisTickOffsets(range, n)`: неделя — все дни, месяц — смещения 0, 7, 14, 21 и
  последнее, остальное — 5 через `pickXLabelIndices`. Текст — `chartTickLabel(range)` (день
  недели / «26.10» / месяц). У столбиков подпись в ячейке столбика; у линий —
  `PeriodXAxisLabels` ставит подпись под x-позицией дня (`Layout`, с прижатием к краям).
  Модели линий несут `tickDays` и `tickRange`; без периода работают прежние подписи.
- **Карточки.** «Дефицит калорий» — `CalorieDeficitChart` внутри `BlockChartCard(title)` на
  «Приёмах пищи» вместо `CalorieSection`. На «Весе» `BodySection` выводится без внешней
  карточки «Прогресс»: `title` = «Вес», `weightLocked` добавляет вес к выбранным рядам и прячет
  его кнопку, `rangePicker` = `DiaryRangePicker` (общий период + откат «Всего времени» на
  неделю). Тот же переключатель — в каждой `MeasurementPlotCard`.
- **Период по умолчанию.** `ProgressViewModel` при создании берёт период из настройки
  (`TimeRange.resolve(null, default)`, заводская «Неделя»); последний просмотренный ещё
  сохраняется, но не читается.

## Риски / Компромиссы

- **Двойной учёт активности в дефиците.** Норма (TDEE) уже включает уровень активности из профиля,
  а формула из ТЗ ещё раз прибавляет тренировки. Сделано как в ТЗ; при «высоком» уровне активности
  дефицит будет завышен — это стоит учесть в изменении `calorie-deficit-control`.
- **Ккал Health Connect по дням** есть, только если включено «Энергия из Health Connect» (в опросе
  включается) и есть доступ; без них в дефицит входят только ручные тренировки.

- **`HomeScreen` в режиме блока** — больше всего риск у условий в `LazyColumn`: можно
  случайно спрятать нужную строку. Нужна ручная проверка по чек-листу: голодание, кофеин,
  заметка дня, режим выбора, вставка скопированных записей, разбор фото.
- **История Health Connect** — без `READ_HEALTH_DATA_HISTORY` Health Connect отдаёт данные
  только за 30 дней до выдачи разрешения. Длинные периоды покажут пустое начало. Это честное
  «нет данных», не ошибка.
- **Чтение Health Connect на каждом экране** — до 365 дней агрегатов. Чтение идёт один раз на
  смену периода или дня, в `viewModelScope`. Пока данные грузятся, показывается
  `ChartPlaceholder`.
- **Общий `ProgressViewModel` из записи HOME** живёт, пока жив Дневник. Его подписки
  (вес, журнал целей) работают и на дашборде. Это приемлемо: раньше Прогресс тоже был
  вкладкой.
- **Состояние вкладок** — переход на «Профиль» с экрана блока сохраняет стек Дневника
  (`saveState`). Возврат на «Дневник» восстановит экран блока, а повторное нажатие —
  дашборд. Это стандартное поведение Android, проверить вручную.
