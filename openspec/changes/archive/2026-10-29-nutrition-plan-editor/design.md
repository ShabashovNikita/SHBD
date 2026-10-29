# Дизайн: «Твой план» из карточки «Питание»

## Контекст

Экран опроса «Твой план» (`PlanReadyStep`) берёт данные из `OnboardingState`: план =
`customCalories` либо норма + поправка темпа (`calorieAdjustment`) в пределах
`planCalorieRange`, срок — `planDaysToGoal`, предупреждение — `planNeedsDoctorWarning`. Пункт
«Изменить цель калорий» в карточке «Питание» сейчас открывает `SETTINGS_GOALS`.

## Решения

### 1. Общая разметка
Из `PlanReadyStep` выносится `PlanCaloriesContent(planProfile, normKcal, planKcal, onPlanChange)`
— число «план из нормы», ползунок, карточка дефицита и срока, БЖУ, предупреждение. Она не
прокручивается сама: опрос и новый экран кладут её в свою прокручиваемую колонку. Заголовок и
пояснение об адаптивных целях остаются у вызывающего экрана. Цель, вес и желаемый вес берутся из
`planProfile` (`goal`, `weightKg`, `goalWeightKg`).

### 2. Расчёт по текущему весу
Чистые функции (в `OnboardingViewModel.kt`, рядом с `planCalorieRange`):
- `planBaseProfile(profile, latestWeightKg)` — профиль с последним весом и без своих калорий и
  БЖУ (`customCalories/Protein/Carbs/Fat = null`), чтобы БЖУ делились из плана, как в опросе;
- `defaultPlanKcal(base)` — норма (`tdee`, округлённая) + `calorieAdjustment` в пределах
  `planCalorieRange`; этим же пользуется `OnboardingState.planKcal`;
- `planProfileFor(base, planKcal)` — `base` с `customCalories = planKcal` (или без своих калорий,
  если план совпал с `dailyCalories`).
Последний вес — `weightRepository.latest`, если записей нет — вес профиля.

### 3. Экран `CaloriePlanScreen`
Маршрут `ShbdRoutes.CALORIE_PLAN` = "calorie-plan". Верхняя панель с «Назад» и заголовком «Твой
план», под ней прокручиваемая колонка с подзаголовком и `PlanCaloriesContent`, внизу кнопка
«Сохранить» над панелью навигации. Ползунок меняет локальное состояние; «Сохранить» вызывает
`profileRepository.save(planProfileFor(base, plan))` и закрывает экран. Прочие поля профиля
(цели по дням, фазы, замки, режим белка) не меняются. Цель с замком калорий (`caloriesLocked`)
не мешает: пользователь явно задаёт калории.

### 4. Навигация
`DiaryDashboardScreen(onOpenGoals)` в `ShbdNavHost` открывает `CALORIE_PLAN` вместо
`SETTINGS_GOALS`. Раздел Профиль → Цели остаётся доступен из Профиля.

### 5. Без «Планирования приёмов пищи»
Из `FoodEntrySettingsScreen` удаляются переключатель `settings_meal_planning` и диалог
пояснения. Одноразовый шаг `disableMealPlanningOnceImpl()` (флаг `mealPlanningRetired`) при
запуске ставит `mealPlanningEnabled = false`, иначе включённый ранее режим остался бы без
выключателя. Код режима планирования на Главной остаётся (он не виден при выключенном флаге).

## Риски / компромиссы

- Сохранение сбрасывает свои БЖУ пользователя (`customProtein/Carbs/Fat`) — так же, как в опросе
  БЖУ делятся из калорий. Тонкая настройка БЖУ остаётся в Профиль → Цели.
- Ползунок не опускает план до BMR автоматически; предупреждение показывается при дефиците
  больше 1 100 ккал в день.

## Дополнение (29.10.2026)

### 6. Желаемый вес
`planBaseProfile` заполняет `goalWeightKg` при сбросе и наборе: сохранённый, иначе
`defaultGoalWeightKg(вес, цель)` из опроса. `CaloriePlanScreen` под `PlanCaloriesContent`
показывает карточку `GoalWeightCard`: заголовок «Желаемый вес», пояснение с текущим весом и
целью по весу, колесо `OnboardingWeightWheel` (высота колеса фиксированная, поэтому оно
работает внутри прокручиваемой колонки). Выбор колеса меняет `goalWeightKg` в состоянии экрана —
срок в карточке дефицита пересчитывается; «Сохранить» записывает его вместе с калориями. При
«Поддерживать» карточки нет.

### 7. «Твой план» из «Приёмов пищи»
Меню «⋮» карточки «Питание» вынесено в `CalorieGoalMenu(onChangeCalorieGoal)` и стоит также в
сводке экрана «Приёмы пищи» (`trailing` у `NutritionSummaryContent`). `SummaryCard` получил
необязательный `onClick` (Surface с нажатием); сводка «Приёмов пищи» по нажатию открывает
`CALORIE_PLAN` через `DiaryBlockActions.onOpenCaloriePlan`. «Назад» с «Твоего плана» возвращает
на «Приёмы пищи».
