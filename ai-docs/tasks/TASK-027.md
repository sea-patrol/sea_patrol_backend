# TASK-027 - Backend часть: in-memory static catalogs без БД

## Метаданные
- **ID:** `TASK-027`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `Medium`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 3 / TASK-027`
- **Трек:** `Backend`
- **Depends on:** `TASK-023`

## Контекст
После вынесения `H2` в самую позднюю волну roadmap у backend всё ещё не было явного in-memory слоя для статических игровых определений. Будущие cargo/trade/quest flows упирались в отсутствие единого runtime source of truth для `ship classes`, `items`, `merchants` и `quests`, загружаемого без БД.

## Цель
Добавить backend static catalogs как resource-driven in-memory registry, чтобы процесс поднимался и работал только на `src/main/resources/*` и памяти процесса, без `Liquibase`/`H2` в основных игровых сценариях.

## Source of Truth
- Код / ресурсы:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/catalog/StaticCatalogRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/catalog/ShipClassDefinition.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/catalog/ItemDefinition.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/catalog/MerchantDefinition.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/catalog/QuestDefinition.java`
  - `sea_patrol_backend/src/main/resources/catalogs/ship-classes.json`
  - `sea_patrol_backend/src/main/resources/catalogs/items.json`
  - `sea_patrol_backend/src/main/resources/catalogs/merchants.json`
  - `sea_patrol_backend/src/main/resources/catalogs/quests.json`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/game/catalog/StaticCatalogRegistryTest.java`
  - `sea_patrol_backend/src/test/resources/test-catalogs-broken-merchant/*`
- Документация:
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Static catalogs доступны через runtime registry/service.
- [x] Пустой стенд поднимается и работает только на in-memory данных + resource files.
- [x] Нет зависимости на `Liquibase`/`H2` в основных игровых flow.

## Scope
**Включает:**
- resource-driven загрузку `ship classes`, `items`, `merchants`, `quests`;
- in-memory lookup API для будущих backend flows;
- fail-fast валидацию дубликатов и merchant/item, quest/item ссылок;
- test coverage на успешную загрузку и broken catalog scenario.

**Не включает (out of scope):**
- публичные REST endpoints для выдачи этих каталогов;
- runtime cargo/trade/quest mechanics;
- любую БД, миграции или persistence.

## Технический подход
Я добавил `StaticCatalogRegistry` как Spring `@Service`, который на старте читает `src/main/resources/catalogs/*.json` через `ObjectMapper` и держит definitions в памяти процесса. Каждый catalog индексируется по `id`, а дубликаты режутся fail-fast при bootstrap. Для merchant/quest definitions добавлена базовая reference validation: merchant не может ссылаться на несуществующий item, а item-based quest не может ссылаться на неизвестный target item.

Структура каталогов выбрана прикладная, а не абстрактная: отдельные JSON-файлы для `ship-classes`, `items`, `merchants`, `quests`, с простыми typed records. Это уже даёт опорный runtime слой для будущих `cargo`, `trade`, `quest` задач, но не навязывает premature API surface.

## Контракты и данные
### Runtime catalogs
- `ship-classes.json` — базовые характеристики кораблей.
- `items.json` — типы предметов и базовые параметры stack/price.
- `merchants.json` — торговцы и их inventory item ids.
- `quests.json` — минимальные quest definitions (`deliver-wood-01`, `catch-fish-01`, `sink-pirate-01`).

### API / WebSocket
- Внешний REST/WS contract не меняется.
- Изменение ограничено backend runtime architecture и resource layout.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить загрузку static catalogs и отсутствие регрессий по backend suite | `Passed` |

### Ручная проверка
- [x] `StaticCatalogRegistry` поднимает production catalogs из `src/main/resources/catalogs/*`
- [x] `getShipClass/getItem/getMerchant/getQuest` отдают ожидаемые definitions
- [x] broken merchant catalog ссылается на missing item и валится fail-fast

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Static catalogs пока доступны только через backend service layer; публичный catalog/read API для frontend или tooling остаётся отдельной задачей, если реально понадобится | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Static catalogs добавлены в resources
- [x] Runtime registry реализован
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
