# Дизайн: «Твой план» следует темпу

## Контекст

Шаг «Как быстро…» (`GoalSpeedStep`) показывает срок `разница веса ÷ темп × 7`. Шаг «Твой план»
(`PlanReadyStep`) брал калории `profile.effectiveCalories` = `customCalories ?: dailyCalories`, где
`dailyCalories = CalorieSafety.clampAuto(TDEE ± темп, BMR, TDEE)` — не ниже max(BMR, 1 200).
У большинства пользователей TDEE − 1 100 < BMR, поэтому при темпе 1 кг/нед план поднимался до BMR,
дефицит уменьшался, а срок растягивался. Шаг «Создаём план» мог также подставить калории,
посчитанные ИИ, если модель на устройстве уже загружена. Ручной выбор ползунка оставался в
состоянии и после возврата к шагу темпа.

## Решения

### 1. Калории плана
В `OnboardingState` новое свойство `planKcal`: `customCalories` (выбор ползунка) либо
`норма + calorieAdjustment` (норма — `tdee.roundToInt()`, поправка — ± `темп × 7 700 ÷ 7`), в
пределах `planCalorieRange`. Новая `buildPlanProfile()` = `buildProfile()` с `customCalories =
planKcal`, если он отличается от `dailyCalories`. `PlanReadyStep` (калории, БЖУ, срок) и
`complete()` (сохранение) используют `buildPlanProfile()`. Срок считается тем же
`planDaysToGoal`: при дефиците темпа он равен сроку шага «Как быстро…».

### 2. Сброс ручного выбора
`setWeeklyChange`, `setGoal`, `setGoalWeight`, `setActivity`, `setWeight` обнуляют
`customCalories` и БЖУ и флаг `planEdited` — план снова следует темпу.

### 3. Без подмены ИИ
`buildPlanWithAI` больше не вызывает расчёт целей ИИ — шаг «Создаём план» только показывает
анимацию и вызывает `onDone`. Пересчёт целей с ИИ остаётся в Профиль → Цели.

### 4. Предупреждение
Чистые функции в `OnboardingViewModel.kt`:
- `PLAN_WARNING_DEFICIT_KCAL = dailyCalorieAdjustmentForWeeklyRateKg(1.0)` (1 100);
- `planNeedsDoctorWarning(goal, normKcal, planKcal)` — `goal == LOSE` и `normKcal − planKcal >
  1 100`;
- `planMinRecommendedKcal(normKcal)` — `normKcal − 1 100`, не меньше 0 (см. решение 6).
Карточка: заголовок «Пожалуйста, проконсультируйтесь с врачом или вашим тренером», строка
«Минимум: 1 400 ккал в день» — `maxLines = 1`, `softWrap = false`.

### 5. Подпись об ограничении темпа
Параметр `paceCappedTarget` у `GoalSpeedStep` и строка `onboarding_pace_capped` удаляются.

## Риски / компромиссы

- [План по темпу 1 кг/нед может быть ниже BMR] → это выбор пользователя; при дефиците больше
  1 100 ккал показывается предупреждение; адаптивные цели позже корректируют план по факту.
- [ИИ больше не считает план в опросе] → план по формуле предсказуем и совпадает с темпом.

## Дополнение (29.10.2026)

### 5. «Назад» с «Твоего плана»
`OnboardingViewModel.back()` пропускает шаг `BUILDING_PLAN`: с `PLAN_READY` переход сразу на
`HEALTH_CONNECT`. Чистая функция `previousOnboardingStep(step)` покрыта юнит-тестом.

## Дополнение (29.10.2026)

### 6. Минимум без границы 1 200 ккал
`planMinRecommendedKcal(normKcal)` = `normKcal − PLAN_WARNING_DEFICIT_KCAL`, не меньше 0: это план
с темпом 1 кг в неделю. Прежняя граница `CalorieSafety.ABSOLUTE_FLOOR_KCAL` (1 200) при обычной
норме 2 000–2 300 ккал всегда давала «Минимум: 1 200 ккал». Между заголовком и строкой минимума —
отступ 6 dp.
