# Задачи: исправить нестабильные юнит-тесты

## 1. FoodEntryEditMathTest

- [x] 1.1 Добавить `@Before`/`@After`: сохранить `Locale.getDefault()`, поставить `Locale.US`, после теста вернуть. Проверка: тесты `displayD_*` и `editD_*` проходят на русской Windows.
- [x] 1.2 Добавить тест `displayD_usesRussianDecimalComma`: при `Locale("ru")` `displayD(21.3) == "21,3"`, `editD(21.3) == "21,3"`, `baseOptionalFromText("21,3")` возвращает 10.65. Проверка: тест проходит.

## 2. RetryPolicyBodyReadTest

- [x] 2.1 В `bodyReadTimeout_mapsToNetworkError` принимать `timeout` или `timed out` (без учёта регистра), с комментарием о двух таймерах. Проверка: тест проходит.

## 3. Проверка и документация

- [x] 3.1 Прогнать `gradlew :app:testDebugUnitTest`. Проверка: 0 падений.
- [x] 3.2 Обновить журнал прогресса и отметить задачу 4.3 в `main-navigation-4-tabs`. Проверка: файлы обновлены.
