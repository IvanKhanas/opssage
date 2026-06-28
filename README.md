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
      └── AI Agent Service ──── Kafka ──── Skills Service (SRE MCP Server)
           │                                   ├── VictoriaMetrics
           │                                   ├── VictoriaLogs
           │                                   ├── Kubernetes API
           │                                   └── Distributed tracing
           └── MCP ──── Knowledge Service
                            └── MongoDB (service profiles, runbooks, known incidents)
```

| Service | Role |
|---|---|
| AI Agent Service | LLM orchestration, investigation workflow, chat history stored in MongoDB |
| Skills Service | Read-only MCP server for observability and Kubernetes diagnostics |
| Knowledge Service | MCP server for platform knowledge: service profiles, runbooks, known incidents, investigation facts |
| Admin Service | REST API backend for the UI — starts investigations, returns reports, moderates knowledge |
| Web UI | Interface for SRE engineers and analysts |

The agent publishes investigation tasks to Kafka. The Skills Service picks them up, runs the diagnostic steps, and publishes results back.

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

Kotlin, Spring Boot 4, Spring AI for LLM integration and MCP client/server. MongoDB stores chat history, investigation records, and the knowledge base. Kafka handles async communication between the agent and the Skills Service. VictoriaMetrics and VictoriaLogs are the observability backend; the Kubernetes API is used for cluster event and workload inspection. Gradle multi-module build with ktlint, Spotless, JaCoCo, and SonarQube.

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
