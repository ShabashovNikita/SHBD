# Дизайн: дашборд вкладки «Дневник»

## Контекст

Что уже есть в коде (папка `app/src/main/java/app/shbd/`):

- **Прежняя Главная**: `ui/home/HomeScreen.kt` (2449 строк) и `HomeViewModel.kt` (4426 строк).
  Здесь считаются выбранный день (`setSelectedDate`), съеденные калории
  (`loggedCaloriesToday`), цель (`heroCalorieGoal`, с учётом дефицита, типа дня и адаптивных
  целей), БЖУ (`HomeTopNutrient.current/goal`), вода (`waterTodayMl`, `waterDailyGoalMl`,
  `addWater`), шаги (`activitySnapshot.steps`, `homeDisplay.stepGoal`), активные ккал
  (`displayActiveCalories`), ручная активность (`addManualActive`, `ManualActiveSheet`).
  На этом же экране все листы добавления еды и обработка входящих фото и ярлыков
  (`sharedImageInbox`, `shortcutEntryInbox`).
- **Неделя**: `ui/components/WeekStrip.kt` → `WeekEnergyStrip` (листается, точки типов дня,
  отметка «не отслеживается»). Окно дат строит `weekStripDates()` в `HomeScreen.kt`.
- **Health Connect**: `container.health.readDailyActivity(days)` возвращает шаги и минуты
  тренировок, `readDailyWellness(days)` — минуты сна. `capabilities()` сообщает, какие
  разрешения выданы (`stepsRead`, `sleepRead`, …).
- **Вес**: `container.weightRepository.entries` (Flow), диалог `AddWeightDialog` в
  `ui/progress/ProgressDialogs.kt`.
- **Прогресс**: `ui/progress/ProgressScreen.kt`.
- **Навигация**: `ShbdNavHost.kt`. Вкладка HOME сейчас рисует `DiaryScreen` с переключателем
  `DiarySegment`.

## Цели / Не цели

**Цели:**
- Сделать дашборд по макету из ТЗ (описан в proposal), данные взять из готовой логики `HomeViewModel`, не
  дублировать расчёт цели калорий.
- Сохранить все функции Главной и Прогресса: они временно доступны как вложенные экраны.
- Подготовить место под «⋮» (изменение 5) и под экраны блоков (изменение 4).

**Не цели:**
- Экраны блоков с графиками (`diary-block-screens`).
- Логика дефицита в «⋮» (`calorie-deficit-control`).
- Переделка `HomeViewModel` или перенос логики из него.

## Решения

### 1. Один `HomeViewModel` на дашборд и экран «Приёмы пищи»

Дашборд берёт `HomeViewModel` из записи навигации HOME (`viewModel(factory = HomeViewModel.Factory(container))`).
Экран `diary/food` рисует тот же `HomeScreen`, но через
`CompositionLocalProvider(LocalViewModelStoreOwner provides nav.getBackStackEntry(HOME))`.
`HomeScreen` внутри вызывает `viewModel(...)` и получает **тот же экземпляр**.

Почему так:
- выбранный день общий: выбрал вчера на дашборде, открыл «Приёмы пищи» — там тоже вчера;
- цель калорий и БЖУ считаются в одном месте, расхождений быть не может;
- нет второй копии тяжёлой модели с её подписками.

Альтернатива — новая модель, которая сама считает калории и цель. Отвергнута: расчёт цели в
`HomeViewModel` сложный (типы дня, адаптивные цели, фазы), копия разойдётся с оригиналом.

### 2. Лёгкий `DiaryDashboardViewModel` для сна, тренировок и веса

`HomeViewModel` не читает сон и минуты тренировок, а вес хранится отдельно. Новая небольшая
модель:
- принимает выбранный день;
- читает `readDailyActivity` и `readDailyWellness` за окно до 30 дней (окно, в которое попадает
  выбранный день; для более старых дат — «Нет данных»);
- подписывается на `weightRepository.entries`;
- отдаёт `DashboardExtras(exerciseMinutes, sleepMinutes, latestWeight, latestWeightDate,
  stepsGranted, sleepGranted)`.

Чистые функции выносятся отдельно и покрываются юнит-тестами:
- `latestWeightOnOrBefore(entries, date)`;
- `formatSleep(minutes)` → «7 ч 30 мин»;
- `progressFraction(current, goal)` с ограничением 0..1.

### 3. Маршруты и вложенные экраны

- `ShbdRoutes.DIARY_FOOD = "diary/food?add={add}"` — `DiaryFoodScreen`: `Scaffold` с
  `TopAppBar` «Приёмы пищи» и стрелкой «Назад», внутри `HomeScreen`. Параметр `add=true`
  передаётся в `HomeScreen(openAddFoodOnStart = true)`: при первом показе он ставит
  `showAddFoodSheet = true`.
- `ShbdRoutes.PROGRESS` снова становится `composable`: `DiaryProgressScreen`, то есть
  `ProgressScreen` под `TopAppBar` «Прогресс» со стрелкой «Назад».
- Оба маршрута не входят в `bottomTabs`, поэтому нижнее меню на них скрыто автоматически
  (`showTabs`).
- Входящие фото и ярлыки сейчас возвращают на HOME, потому что их обрабатывал `HomeScreen`.
  Теперь `HomeScreen` живёт на `DIARY_FOOD`, поэтому эффекты в `ShbdNavHost` переходят на
  `DIARY_FOOD` (сверху HOME, `launchSingleTop`). Пока открыт онбординг, они ничего не делают,
  как и раньше.
- Есть риск двойной обработки: дашборд не вызывает `HomeScreen`, поэтому ящики с фото и ярлыками
  он не опустошает, их обрабатывает только `DIARY_FOOD`.
- `shbd://go/progress`: `dest = PROGRESS`, переход поверх HOME. Особая обработка через
  `diarySegment` удаляется.

### 4. Вёрстка дашборда

- `LazyColumn` с отступом статус-бара и `BottomNavScrollPadding` снизу. Фон — `background`
  темы. Мягкий градиент с образца откладываем: в тёмной теме он смотрится плохо.
- Карточки — `Surface` / `ElevatedCard` с `RoundedCornerShape(24.dp)`, `surface` и
  `surfaceContainer` из темы, поэтому тёмная тема работает сама.
- Карточка недели:
  - заголовок `LocalDate.format("LLLL yyyy")` по языку приложения (с заглавной буквы);
  - значок `Icons.Outlined.CalendarMonth` открывает Material3 `DatePickerDialog`
    (как в `EditFoodEntrySheet`);
  - ниже `WeekEnergyStrip` (листание, точки типов дня).
  Выделение дня делает сам `WeekEnergyStrip` в своём стиле, а не рамкой с образца. Перерисовка
  под образец — косметика, её можно сделать позже отдельной задачей.
- Карточка «Питание»:
  - крупное число калорий, «/ цель ккал» приглушённым цветом, справа `IconButton(MoreVert)`;
  - три колонки БЖУ с `LinearProgressIndicator`, цвета из `nutrientAccentColor()`;
  - строка-кнопка «Приёмы пищи ›» и квадратная кнопка «+» на `surfaceContainerHigh`.
- Общий компонент `DashboardCard(icon, title, content, linkLabel?, onLink?, onAdd?)`.
  Строка «› +» есть только там, где есть ссылка; у Шагов, Сна и Веса нажимается вся карточка.
- Единицы: калории через `LocalEnergyUnit` (ккал/кДж), вода через `WaterAmountFormat`,
  вес через `weightMetric` (кг/фунты).

### 5. «⋮» у калорий

`DropdownMenu` с одним пунктом «Изменить цель калорий» → `SETTINGS_GOALS`. Изменение
`calorie-deficit-control` заменит это меню на свой лист и переиспользует место кнопки.

### 6. Удаление `DiaryScreen`

Удаляются `ui/diary/DiaryScreen.kt`, `DiarySegment`, `diarySegment` в `ShbdNavHost` и
строки `diary_segment_today/progress` (en, ru). Компонент `ShbdSegmentedTabs` остаётся:
его используют другие экраны.

### 7. Ручные шаги и сон (доработка 26.10.2026)

- Хранилище: `data/ManualDayMetricsRepository.kt`, список `ManualDayMetric(date, steps?, sleepMinutes?)`
  в DataStore (ключ `manualDayMetrics`, JSON-список через `listPref`/`editListPref`). Запись с обоими
  пустыми полями удаляется. Ограничения: до 200 000 шагов, до 24 ч сна. Репозиторий — в
  `AppContainer.manualDayMetricsRepository`.
- Правило показа: `manual ?: healthConnect`. Ручное значение показывается даже без разрешений
  Health Connect; «Подключить» — только если нет ни того, ни другого.
- Ввод: `ui/diary/ManualMetricDialogs.kt` — `ManualStepsDialog`, `ManualSleepDialog` и общий
  `ManualMetricEditors` (используют и дашборд, и экраны блоков). Поля предзаполнены ручным значением
  или значением Health Connect.
- В Health Connect не пишем: для этого нужно разрешение на запись, а ручное значение — это
  поправка пользователя, а не данные устройства.

### 8. Цель калорий фиксирована; карточка «Тренировки»

Первая доработка прибавляла тренировки к цели (`ADD_ACTIVE` + `fullCredit`, миграция раскладки
v3). По второму замечанию это отменено: цель дня не зависит от тренировок.
- Дашборд и экран «Приёмы пищи» берут цель из `HomeUiState.baseCalorieGoal` (цель дня по плану
  `resolvedDayTargets`), а не из `heroCalorieGoal` (там учитывается сожжённое), и передают в
  `HomeTopNutrient.goal` масштаб БЖУ `1f` вместо `macroGoalScale`.
- Миграция раскладки **v4** (`migrateHomeDisplayLayoutIfNeeded`) один раз ставит
  `HOME_CALORIE_DISPLAY_MODE = STATIC` и `HOME_CALORIE_FULL_CREDIT = false` — и на новых, и на уже
  установленных приложениях, где успела сработать v3. Так виджет и старые экраны тоже не
  прибавляют автоматическое сожжённое.
- Карточка «Тренировки» (`ActivityCard` → общий `ActivityValues`, он же в сводке экрана
  «Тренировки») показывает `HomeUiState.liveActiveBurn` — ккал Health Connect + ручные записи,
  только для сведения. Ссылка — новая строка `diary_workouts_all`; заголовок экрана блока
  остаётся «Тренировки» (`diary_workouts_link`).
- Порядок карточек: Питание, Тренировки, Вес, Шаги, Вода, Сон.
- Кнопки добавления: `HeaderAddButton` («+» в заголовке) — общая для «Воды», «Веса», «Шагов» и
  «Сна»; `ManualEditButton` (карандаш) удалён.

### 9. Размеры и отступ под меню

- `DashboardCard`: отступы 16/14 dp, иконка 18 dp, заголовок `titleSmall`, кнопки «›» и «+» 44 dp.
- Крупные числа — общая константа `ValueFontSize = 30.sp` (калории, тренировки, шаги, вода, сон, вес).
- Нижнее меню рисуется поверх экранов (внешний `Scaffold` не передаёт внутренний отступ), а
  `BottomNavScrollPadding` (80 dp) не учитывал системную панель навигации. Новая функция
  `bottomNavContentPadding(extra)` = 80 dp + высота панели навигации + `extra`; её используют
  дашборд, каркас экранов блоков и `HomeScreen` в режиме блока.

## Риски / Компромиссы

- **`HomeScreen` на вложенном маршруте** → у него свои `Scaffold` и FAB. Проверить, что FAB
  не перекрывается и нет двойного отступа статус-бара под `TopAppBar` (`contentWindowInsets`).
- **Общая модель через `getBackStackEntry(HOME)`** → если HOME нет в стеке (онбординг), будет
  падение. На `DIARY_FOOD` можно попасть только после онбординга. Для надёжности вызов
  обёрнут в `runCatching`, при неудаче берётся своя модель.
- **Health Connect на эмуляторе** без данных → карточки покажут «Нет данных». Для проверки есть
  отладочный генератор тестовых данных (`TestDataSeeder`, Настройки → Данные → примеры), а шаги можно
  добавить в приложении Health Connect.
- **Скриншот-тесты** `ReleaseScreenshotPreviews` рисуют `HomeScreenPreviewContent`, а не
  дашборд, поэтому их эталоны не ломаются. Превью дашборда добавляется как `@Preview`.
