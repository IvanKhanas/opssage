# OpsSage

OpsSage investigates incidents in Kubernetes microservice clusters. It talks to your existing observability stack, pulls diagnostic facts, and writes a structured report — without touching anything in the cluster.

The LLM acts as a coordinator. Every infrastructure call goes through a fixed set of read-only MCP tools. The model reasons about what it sees; it does not act on it.

## The problem

On-call engineers at large microservice companies spend a chunk of every incident doing the same thing: pulling metrics, scanning logs, checking whether someone deployed recently, following errors through service dependencies. That initial picture can take 20-40 minutes to build before any real debugging begins.

OpsSage does that first pass. It queries VictoriaMetrics, VictoriaLogs, distributed traces, and the Kubernetes API, then hands you a report with the evidence attached.

## Architecture

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

| Service | Role |
|---|---|
| AI Agent Service | LLM orchestration, investigation workflow, chat history stored in MongoDB |
| Skills Service | Read-only MCP server for observability and Kubernetes diagnostics |
| Knowledge Service | MCP server for platform knowledge: service profiles, runbooks, known incidents, investigation facts |
| Admin Service | REST API backend for the UI — starts investigations, returns reports, moderates knowledge |
| Web UI | Interface for SRE engineers and analysts |

Admin Service publishes user and system investigation commands to Kafka. AI Agent Service consumes those commands and runs the investigation. The agent talks to SRE and Knowledge only through bounded read-only MCP tools. Kafka is not used between the agent and SRE tools because that boundary must stay interactive, tool-scoped, and masked before model consumption.

Default command topic:

```text
opssage.investigation.requests
```

Investigation commands and results are published through a transactional
outbox. The service first commits its MongoDB state and an outbox event in the
same transaction, then a scheduled publisher delivers the event to Kafka.
Kafka consumer failures are retried and then routed to dedicated DLQ topics:

```text
opssage.investigation.requests.dlq
opssage.investigation.results.dlq
```

The direct `agent-service` REST endpoint is kept for local development and internal smoke tests. The production entrypoint for UI traffic is `admin-service`.

## Authentication boundary

Admin Service is the public gateway. Browser sessions use short-lived JWT
access tokens stored in `Secure`, `HttpOnly`, `SameSite=Strict` cookies. Refresh
tokens are opaque random values, also stored in secure cookies, and only their
hashes are persisted in MongoDB.

User identity is server-owned. Request bodies never decide `userId`, roles, or
permissions. When Admin Service publishes an investigation command to Kafka, it
adds `requestedBy` from the verified security context.

For local HTTP development set:

```bash
ADMIN_AUTH_COOKIE_SECURE=false
```

## Investigation types

- `USER_PROBLEM_INVESTIGATION` — user-reported service problem
- `ROLLOUT_HEALTH_CHECK` — automatic check after a new deployment
- `ALERT_INVESTIGATION` — triggered by a vmalert or Alertmanager webhook
- `ANALYTICAL_REQUEST` — manual log and metric analysis
- `GENERAL_SERVICE_INVESTIGATION` — free-form service health check

## What the system will not do

The read-only constraint is architectural, not just a policy:

- No rollbacks, pod restarts, or changes to Kubernetes resources
- Queries to VictoriaMetrics and VictoriaLogs go through predefined tool templates, not arbitrary strings the LLM generates
- The LLM sees aggregated diagnostic facts, not raw log streams
- Facts the agent proposes are stored as `PROPOSED` — an engineer approves them before they enter the knowledge base

## Investigation report

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

## Tech stack

Kotlin, Spring Boot 4, Spring AI for LLM integration and MCP client/server. MongoDB stores chat history, investigation records, and the knowledge base. Kafka handles async communication between Admin Service and AI Agent Service. VictoriaMetrics and VictoriaLogs are the observability backend; the Kubernetes API is used for cluster event and workload inspection. Gradle multi-module build with ktlint, Spotless, Kover, and SonarQube.

## Modules

```
opssage/
├── agent-service/        # LLM orchestration, Spring AI, MongoDB
├── sre-mcp-server/       # Read-only diagnostic MCP tools
├── knowledge-mcp-server/ # Platform knowledge base
└── admin-service/        # REST API for UI
```

## Build

```bash
./gradlew build
./gradlew test
./gradlew ktlintCheck
./gradlew spotlessCheck
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
