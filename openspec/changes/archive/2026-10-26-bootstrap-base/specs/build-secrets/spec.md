# Спецификация: секреты сборки

## Purpose

Гарантирует, что ключи API, ключи подписи, личные пути и настройки разработчика никогда не попадают
в git-репозиторий SHBD, а сборка при этом находит их в локальных файлах.

## ADDED Requirements

### Requirement: Секреты исключены из репозитория
Репозиторий SHALL игнорировать файлы с секретами: `secrets.properties` (и варианты
`secrets.properties.*`), `keystore.properties`, файлы ключей подписи (`*.jks`, `*.keystore`),
`local.properties`, файлы окружения (`.env`, `.env.*`).

#### Scenario: Ключ API в secrets.properties
- **WHEN** разработчик создаёт `secrets.properties` с ключом и выполняет `git status`
- **THEN** файл не отображается среди новых файлов

#### Scenario: Ключ подписи
- **WHEN** в любой папке проекта создан файл `release.jks`
- **THEN** git его не отслеживает

### Requirement: Сборочные и личные файлы исключены
Репозиторий SHALL игнорировать сборочные артефакты (`build/`, `.gradle/`, `*.apk`, `*.aab`) и личные
настройки IDE и инструментов (`.idea/`, `*.iml`, `.claude/`, `.qwen/`).

#### Scenario: После сборки
- **WHEN** разработчик выполняет debug-сборку
- **THEN** `git status` не показывает файлов из `build/` и `.gradle/`

### Requirement: Сборка без секретов
Debug-сборка SHALL собираться, даже если файлов с секретами нет; релизная сборка SHALL NOT
содержать ключей API.

#### Scenario: Чистый клон
- **WHEN** проект собирается на машине без `secrets.properties` и `keystore.properties`
- **THEN** `assembleDebug` завершается успешно
