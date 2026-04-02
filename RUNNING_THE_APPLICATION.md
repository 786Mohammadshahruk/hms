# Hospital Management System — Running Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java (JDK) | 21 | Running Spring Boot services |
| Maven | 3.8+ | Building services |
| PostgreSQL | 16 | Primary database (runs as local service) |
| Redis | 7 | JWT blacklist + caching (runs locally) |
| Apache Kafka | Any | Message broker (with Zookeeper) |
| Postman / curl | Any | Testing APIs |

---

## System Architecture

```
  Client (Postman)
        │
        ▼
  API Gateway :8080
        │  ← Validates JWT, stamps X-Internal-Secret, injects X-User-* headers
        │
        ├──► User Service         :8081  (auth, user management)
        ├──► Appointment Service  :8082  (appointments, doctor schedules)
        ├──► Medical Records      :8083  (prescriptions, lab tests)
        ├──► Billing Service      :8084  (bills, payments)
        └──► Notification Service :8085  (notifications)

  All services register with:
        Eureka Server :8761  (service discovery)

  Local Infrastructure:
        PostgreSQL :5432   Redis :6379
        Zookeeper  :2181   Kafka :9092
```

> All API calls go through the **gateway on port 8080**. Direct calls to service ports `:8081`–`:8085` return `403 Forbidden`.

---

## Step 1 — Start Infrastructure

Start all 4 infrastructure components before any Spring Boot service.

### PostgreSQL
PostgreSQL runs as a system service and should already be running after installation.

Verify:
```bash
psql -U hms_user -d hms_db -c "SELECT 1"
```

---

### Redis
```bash
redis-server
```
Wait for: `Ready to accept connections on port 6379`

---

### Zookeeper
```bash
# macOS (Homebrew)
zookeeper-server-start /usr/local/etc/kafka/zookeeper.properties

# Linux / manual install
cd /path/to/kafka
bin/zookeeper-server-start.sh config/zookeeper.properties
```
Wait for: `binding to port 0.0.0.0/0.0.0.0:2181`

---

### Kafka
```bash
# macOS (Homebrew)
kafka-server-start /usr/local/etc/kafka/server.properties

# Linux / manual install
cd /path/to/kafka
bin/kafka-server-start.sh config/server.properties
```
Wait for: `started (kafka.server.KafkaServer)`

---

## Step 2 — One-Time Database Setup

> Skip this if you have already done this before.

Open pgAdmin or psql and run the schema initialisation script:

```bash
psql -U hms_user -d hms_db -f /path/to/hms/scripts/init-schemas.sql
```

Or in pgAdmin:
1. Connect to `hms_db`
2. Open Query Tool
3. Open `scripts/init-schemas.sql` and execute (F5)

You should see: `Query returned successfully`

Verify schemas exist under `hms_db → Schemas`:
```
hms_user         → users, doctor_profiles, patient_profiles, refresh_tokens
hms_appointment  → appointments, doctor_schedules, blocked_slots
hms_medical      → prescriptions, prescription_items, medical_tests
hms_billing      → bills, bill_items, payments
hms_notification → notifications
```

---

## Step 3 — Set Java 21 for Maven

Maven must use JDK 21. Set `JAVA_HOME` before running any `mvn` commands:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Verify
java -version   # should show: openjdk 21...
mvn -version    # should show: Java version: 21...
```

To make this permanent:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
source ~/.zshrc
```

---

## Step 4 — Start Spring Boot Services

Services **must be started in this exact order**. Each one needs its own terminal tab.

### 4.1 — Eureka Server (start first, wait until ready)

```bash
cd /path/to/hms/eureka-server
mvn spring-boot:run
```

Wait until you see:
```
Started EurekaServerApplication in X seconds
```

Verify: Open http://localhost:8761 — Login: `eureka` / `eureka-secret`

---

### 4.2 — User Service

```bash
cd /path/to/hms/user-service
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP",...}
```

---

### 4.3 — Appointment Service

```bash
cd /path/to/hms/appointment-service
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8082/actuator/health
```

---

### 4.4 — Medical Records Service

```bash
cd /path/to/hms/medical-records-service
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8083/actuator/health
```

---

### 4.5 — Billing Service

```bash
cd /path/to/hms/billing-service
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8084/actuator/health
```

---

### 4.6 — Notification Service

```bash
cd /path/to/hms/notification-service
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8085/actuator/health
```

---

### 4.7 — API Gateway (start last)

```bash
cd /path/to/hms/api-gateway
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8080/actuator/health
```

---

## Step 5 — Verify All Services in Eureka

Open the Eureka Dashboard: **http://localhost:8761**
Login: `eureka` / `eureka-secret`

Under **"Instances currently registered with Eureka"** you should see all 5 services:

| Service Name | Expected Instances |
|---|---|
| `APPOINTMENT-SERVICE` | 1 |
| `BILLING-SERVICE` | 1 |
| `MEDICAL-RECORDS-SERVICE` | 1 |
| `NOTIFICATION-SERVICE` | 1 |
| `USER-SERVICE` | 1 |

---

## Step 6 — Test via API Gateway

All API requests go through **`http://localhost:8080`** only.

### Postman Setup

1. Create Environment `HMS Local`
2. Variable: `base_url` = `http://localhost:8080`
3. Variable: `token` = *(empty — auto-filled after login)*

Add to the **Tests** tab of your login request to auto-save the token:
```javascript
const res = pm.response.json();
if (res.data && res.data.accessToken) {
    pm.environment.set("token", res.data.accessToken);
}
```

Use `{{token}}` as the Bearer token in all protected requests.

### Quick Smoke Test

```bash
# Register a patient
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Test","lastName":"User","email":"test@hms.com","password":"Test1@pass","phone":"+1234567890"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@hms.com","password":"Test1@pass"}'
```

---

## Developer Tools

| Tool | URL | Credentials |
|------|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | `eureka` / `eureka-secret` |
| User Service Swagger | http://localhost:8081/swagger-ui.html | None |
| Appointment Swagger | http://localhost:8082/swagger-ui.html | None |
| Medical Records Swagger | http://localhost:8083/swagger-ui.html | None |
| Billing Swagger | http://localhost:8084/swagger-ui.html | None |
| Notification Swagger | http://localhost:8085/swagger-ui.html | None |

> Swagger UIs are available on direct service ports for development. All real traffic must go through the gateway on `:8080`.

---

## Running Tests

```bash
cd /path/to/hms/user-service
mvn test

# All services from root
cd /path/to/hms
mvn test
```

---

## Stopping Everything

```bash
# Stop Spring Boot services
Ctrl+C in each terminal (gateway first, then services, then Eureka)

# Stop Kafka → Zookeeper → Redis
Ctrl+C in each infrastructure terminal (reverse order of startup)
```

---

## Troubleshooting

### `TypeTag :: UNKNOWN` (Maven compiler error)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

### `Connection refused to localhost:5432`
PostgreSQL is not running. Start it via your system's service manager.

### `Connection refused to localhost:9092`
Kafka is not running. Start Zookeeper first, then Kafka.

### `Connection refused to localhost:6379`
Redis is not running. Run `redis-server`.

### `Schema-validation: missing column`
Re-run `scripts/init-schemas.sql` against `hms_db`.

### `Connection refused to localhost:8761`
Eureka is not running yet. Start Eureka first and wait until fully started before launching other services.

### Service not appearing in Eureka
- Ensure Eureka Server is fully started before starting other services
- Check the service's own health: `curl http://localhost:{port}/actuator/health`
- Services take 30-60 seconds to register after startup

### `403 Forbidden` on API call
You are calling a service directly on its port. All calls must go through the gateway at `http://localhost:8080`.

### `401 Unauthorized` from Eureka dashboard
Use credentials: `eureka` / `eureka-secret`

### Port already in use
```bash
lsof -ti:8081 | xargs kill -9
```
Replace `8081` with the blocked port.
