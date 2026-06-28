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
      └── AI Agent Service ──── Kafka ──── Skills Service (SRE MCP Server)
           │                                   ├── VictoriaMetrics
           │                                   ├── VictoriaLogs
           │                                   ├── Kubernetes API
           │                                   └── Distributed tracing
           └── MCP ──── Knowledge Service
                            └── MongoDB (профили сервисов, runbook'и, известные инциденты)
```

| Сервис | Роль |
|---|---|
| AI Agent Service | Оркестрация LLM, workflow расследования, история чатов в MongoDB |
| Skills Service | Read-only MCP-сервер диагностических инструментов для observability и Kubernetes |
| Knowledge Service | MCP-сервер базы знаний: профили сервисов, runbook'и, известные инциденты, факты |
| Admin Service | REST API backend для UI — запуск расследований, отчёты, модерация знаний |
| Web UI | Интерфейс для инженеров сопровождения и аналитиков |

Агент публикует задачи расследования в Kafka. Skills Service их забирает, выполняет диагностические шаги и публикует результаты обратно.

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

Kotlin, Spring Boot 4, Spring AI для интеграции с LLM и MCP client/server. MongoDB хранит историю чатов, записи расследований и базу знаний. Kafka — асинхронное взаимодействие между агентом и Skills Service. VictoriaMetrics и VictoriaLogs — backend наблюдаемости; Kubernetes API — для инспекции событий кластера и состояния workload'ов. Gradle мультимодульная сборка с ktlint, Spotless, JaCoCo и SonarQube.

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
