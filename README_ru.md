# OpsSage

OpsSage расследует инциденты в Kubernetes-кластере микросервисов. Он обращается к существующему observability-стеку, собирает диагностические факты и пишет структурированный отчёт — ничего в кластере при этом не меняя.

LLM выступает координатором. Каждый вызов к инфраструктуре идёт через фиксированный набор read-only MCP-инструментов. Модель рассуждает о том, что видит, но не действует.

## Проблема

Дежурные инженеры в крупных микросервисных командах тратят добрую часть каждого инцидента на одно и то же: смотрят метрики, листают логи, проверяют, не задеплоили ли что-то недавно, прослеживают ошибку через зависимости сервисов. Только чтобы собрать первичную картину, уходит 20-40 минут — и это до начала настоящего расследования.

OpsSage делает этот первый проход. Опрашивает VictoriaMetrics, VictoriaLogs, distributed traces и Kubernetes API, потом отдаёт отчёт с доказательствами.

## Архитектура

```
UI
 └── Admin Service
      └── Kafka ──── AI Agent Service
                       ├── MCP ──── SRE MCP Server
                       │             ├── VictoriaMetrics
                       │             ├── VictoriaLogs
                       │             ├── Kubernetes API
                       │             └── Distributed tracing
                       └── MCP ──── Knowledge Service
                                    └── MongoDB Atlas Local
```

| Сервис | Роль |
|---|---|
| AI Agent Service | Оркестрация LLM, workflow расследования, история чатов в MongoDB |
| Skills Service | Read-only MCP-сервер диагностических инструментов для observability и Kubernetes |
| Knowledge Service | MCP-сервер базы знаний: профили сервисов, runbook'и, известные инциденты, факты |
| Admin Service | REST API backend для UI — запуск расследований, отчёты, модерация знаний |
| Web UI | Интерфейс для инженеров сопровождения и аналитиков |

Admin Service публикует пользовательские и системные команды расследования в Kafka. AI Agent Service читает эти команды и запускает расследование. К SRE и Knowledge агент обращается только через ограниченные read-only MCP-инструменты. Kafka не используется между агентом и SRE-инструментами: эта граница должна оставаться интерактивной, ограниченной конкретными tools и проходить через masking перед передачей модели.

Топик команд по умолчанию:

```text
opssage.investigation.requests
```

Команды расследования и результаты публикуются через transactional outbox.
Сервис сначала сохраняет своё состояние в MongoDB и outbox event в одной
транзакции, затем scheduled publisher доставляет event в Kafka. Ошибки Kafka
consumers ретраятся и после лимита отправляются в отдельные DLQ topics:

```text
opssage.investigation.requests.dlq
opssage.investigation.results.dlq
```

Прямой REST endpoint в `agent-service` остаётся для локальной разработки и внутренних smoke-тестов. Production-вход для UI-трафика — `admin-service`.

## Граница аутентификации

Admin Service — публичный gateway. Браузерная сессия использует короткоживущий
JWT access token в cookie с флагами `Secure`, `HttpOnly`, `SameSite=Strict`.
Refresh token — opaque random value, тоже в secure cookie; в MongoDB хранится
только его hash.

Идентичность пользователя задаёт сервер. Request body никогда не определяет
`userId`, роли или permissions. Когда Admin Service публикует команду
расследования в Kafka, он добавляет `requestedBy` из проверенного security
context.

Для локальной разработки по HTTP:

```bash
ADMIN_AUTH_COOKIE_SECURE=false
```

## Типы расследований

- `USER_PROBLEM_INVESTIGATION` — проблема конкретного пользователя с сервисом
- `ROLLOUT_HEALTH_CHECK` — автоматическая проверка после нового деплоя
- `ALERT_INVESTIGATION` — запускается по webhook от vmalert или Alertmanager
- `ANALYTICAL_REQUEST` — ручной анализ логов и метрик
- `GENERAL_SERVICE_INVESTIGATION` — произвольная проверка здоровья сервиса

## Что система не делает

Read-only — это архитектурное решение, не просто политика:

- Никаких rollback'ов, перезапуска pod'ов и изменений Kubernetes-ресурсов
- Запросы к VictoriaMetrics и VictoriaLogs идут через заранее определённые шаблоны, а не через произвольные строки, которые генерирует LLM
- LLM видит агрегированные диагностические факты, не сырые потоки логов
- Факты, которые предлагает агент, записываются со статусом `PROPOSED` — инженер подтверждает их вручную перед попаданием в базу знаний

## Итоговый отчёт

```json
{
  "investigationId": "inv-001",
  "type": "ROLLOUT_HEALTH_CHECK",
  "status": "problem_found",
  "severity": "high",
  "service": "deposit-service",
  "summary": "...",
  "mostLikelyCause": "...",
  "confidence": "medium",
  "evidence": [],
  "recommendedActions": [],
  "proposedFacts": [],
  "proposedSkills": []
}
```

## Стек

Kotlin, Spring Boot 4, Spring AI для интеграции с LLM и MCP client/server. MongoDB хранит историю чатов, записи расследований и базу знаний. Kafka — асинхронное взаимодействие между Admin Service и AI Agent Service. VictoriaMetrics и VictoriaLogs — backend наблюдаемости; Kubernetes API — для инспекции событий кластера и состояния workload'ов. Gradle мультимодульная сборка с ktlint, Spotless, Kover и SonarQube.

## Модули

```
opssage/
├── agent-service/        # Оркестрация LLM, Spring AI, MongoDB
├── sre-mcp-server/       # Read-only диагностические MCP-инструменты
├── knowledge-mcp-server/ # База знаний платформы
└── admin-service/        # REST API для UI
```

## Сборка

```bash
./gradlew build
./gradlew test
./gradlew ktlintCheck
./gradlew spotlessCheck
```

## Лицензия

Apache License 2.0. См. [LICENSE](LICENSE).
