# Hospital Management System — Running Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java (JDK) | 21 | Running Spring Boot services |
| Maven | 3.8+ | Building services |
| Docker | Any | Running infrastructure containers |
| Colima (macOS) | Any | Docker runtime on macOS (instead of Docker Desktop) |
| Postman / curl | Any | Testing APIs |

---

## System Architecture

```
                        ┌─────────────────────────────────────────────────────┐
                        │              Docker Containers                       │
                        │  PostgreSQL:5432  Redis:6379  Kafka:9092             │
                        │  Zookeeper:2181   Kafka-UI:8090  Redis-UI:8091       │
                        └─────────────────────────────────────────────────────┘

 Client (Postman)
       │
       ▼
 API Gateway :8080
       │
       ├──► User Service         :8081  (auth, user management)
       ├──► Appointment Service  :8082  (appointments, doctor schedules)
       ├──► Medical Records      :8083  (prescriptions, lab tests)
       ├──► Billing Service      :8084  (bills, payments)
       └──► Notification Service :8085  (notifications)

 All services register with:
       Eureka Server :8761  (service discovery)
```

---

## Step 1 — Start Docker Runtime (macOS with Colima)

> Skip this step if you have Docker Desktop running.

```bash
# Start Colima (if not already running)
colima start

# Set Docker host environment variable
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"

# Verify Docker is working
docker ps
```

To make the `DOCKER_HOST` export permanent, add it to your `~/.zshrc`:
```bash
echo 'export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"' >> ~/.zshrc
source ~/.zshrc
```

If you see `docker-credential-desktop not found` error, fix your Docker config:
```bash
echo '{"auths":{}}' > ~/.docker/config.json
```

---

## Step 2 — Start Infrastructure Containers

Navigate to the project root (where `docker-compose.yml` is located):

```bash
cd /path/to/hms

docker-compose up -d
```

This starts 6 containers:

| Container | Port | Description |
|-----------|------|-------------|
| `hms-postgres` | 5432 | PostgreSQL database (all 5 schemas) |
| `hms-redis` | 6379 | Redis (JWT blacklist + caching) |
| `hms-zookeeper` | 2181 | Kafka coordination |
| `hms-kafka` | 9092 | Kafka message broker |
| `hms-kafka-ui` | 8090 | Kafka UI dashboard |
| `hms-redis-ui` | 8091 | Redis Commander UI |

**Verify all containers are healthy:**
```bash
docker ps
```

All containers should show `healthy` status. If any show `starting`, wait 20-30 seconds and check again.

**Check logs if a container fails:**
```bash
docker logs hms-postgres
docker logs hms-kafka
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

Services **must be started in this exact order** because each one depends on Eureka being available before it registers.

### 4.1 — Eureka Server (Service Discovery)

```bash
cd /path/to/hms/eureka-server
mvn spring-boot:run
```

Wait until you see:
```
Started EurekaServerApplication in X seconds
```

**Verify:** Open http://localhost:8761 in your browser.
Login: `eureka` / `eureka-secret`

---

### 4.2 — User Service

Open a **new terminal tab/window**:

```bash
cd /path/to/hms/user-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

Wait until you see:
```
Started UserServiceApplication in X seconds
```

**Verify:**
```bash
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP",...}
```

---

### 4.3 — Appointment Service

New terminal:

```bash
cd /path/to/hms/appointment-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

**Verify:**
```bash
curl http://localhost:8082/actuator/health
```

---

### 4.4 — Medical Records Service

New terminal:

```bash
cd /path/to/hms/medical-records-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

**Verify:**
```bash
curl http://localhost:8083/actuator/health
```

---

### 4.5 — Billing Service

New terminal:

```bash
cd /path/to/hms/billing-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

**Verify:**
```bash
curl http://localhost:8084/actuator/health
```

---

### 4.6 — Notification Service

New terminal:

```bash
cd /path/to/hms/notification-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

**Verify:**
```bash
curl http://localhost:8085/actuator/health
```

---

### 4.7 — API Gateway (Start Last)

New terminal:

```bash
cd /path/to/hms/api-gateway
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

**Verify:**
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

Or check via terminal:
```bash
curl -s -u eureka:eureka-secret \
  -H "Accept: application/json" \
  http://localhost:8761/eureka/apps | \
  python3 -m json.tool | grep '"name"'
```

---

## Step 6 — Test via Postman

All API requests go through the **API Gateway at `http://localhost:8080`**.

### Setup Postman Environment

1. Create a new Environment in Postman named `HMS Local`
2. Add variable: `base_url` = `http://localhost:8080`
3. Add variable: `token` = *(leave empty — filled automatically after login)*

In **each login/register request**, add this to the **Tests** tab to auto-save the token:
```javascript
const res = pm.response.json();
if (res.data && res.data.accessToken) {
    pm.environment.set("token", res.data.accessToken);
}
```

Use `{{token}}` as the Bearer token in the **Authorization** tab of all protected requests.

---

### API Reference

#### Auth

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/auth/register` | None | Register a new patient |
| POST | `/api/v1/auth/login` | None | Login and get JWT token |
| POST | `/api/v1/auth/refresh` | None | Refresh access token |
| POST | `/api/v1/auth/logout` | Bearer | Logout (blacklist token) |

**Register Patient:**
```json
POST /api/v1/auth/register
{
  "firstName": "Alice",
  "lastName": "Walker",
  "email": "alice@example.com",
  "password": "Alice1@pass",
  "phone": "+1234567890"
}
```

**Login:**
```json
POST /api/v1/auth/login
{
  "email": "alice@example.com",
  "password": "Alice1@pass"
}
```
Response includes `accessToken` and `refreshToken`.

---

#### User Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/v1/users/me` | Bearer | Get own profile |
| PUT | `/api/v1/users/me` | Bearer | Update own profile |
| PATCH | `/api/v1/users/me/password` | Bearer | Change password |
| GET | `/api/v1/users/{id}` | Bearer | Get user by ID |
| GET | `/api/v1/users/doctors` | Bearer | List all doctors |
| GET | `/api/v1/users` | Bearer (ADMIN) | List all users |
| POST | `/api/v1/users/admin/create` | Bearer (ADMIN) | Admin creates any user |
| PATCH | `/api/v1/users/admin/{id}/role` | Bearer (ADMIN) | Change user role |
| PATCH | `/api/v1/users/admin/{id}/deactivate` | Bearer (ADMIN) | Deactivate user |
| PATCH | `/api/v1/users/admin/{id}/activate` | Bearer (ADMIN) | Activate user |
| POST | `/api/v1/users/admin/{id}/reset-password` | Bearer (ADMIN) | Reset user password |

**Admin Create Doctor:**
```json
POST /api/v1/users/admin/create
Authorization: Bearer {{admin_token}}
{
  "firstName": "John",
  "lastName": "Smith",
  "email": "drsmith@example.com",
  "password": "Doctor1@pass",
  "role": "DOCTOR",
  "specialization": "Cardiology",
  "licenseNumber": "LIC-001"
}
```

---

#### Appointments

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/appointments` | Bearer (PATIENT) | Book appointment |
| GET | `/api/v1/appointments/my` | Bearer | Get my appointments |
| GET | `/api/v1/appointments/{id}` | Bearer | Get appointment by ID |
| PATCH | `/api/v1/appointments/{id}/cancel` | Bearer | Cancel appointment |
| PATCH | `/api/v1/appointments/{id}/status` | Bearer (DOCTOR) | Update status |

**Book Appointment:**
```json
POST /api/v1/appointments
Authorization: Bearer {{patient_token}}
{
  "doctorId": 2,
  "appointmentDate": "2026-04-10",
  "timeSlot": "10:00",
  "reason": "General checkup"
}
```

---

#### Doctor Schedules

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/schedules` | Bearer (DOCTOR) | Set weekly schedule |
| GET | `/api/v1/schedules/doctor/{doctorId}` | Bearer | Get doctor's schedules |
| GET | `/api/v1/schedules/doctor/{doctorId}/slots` | Bearer | Get available slots for date |
| POST | `/api/v1/schedules/{id}/block` | Bearer (DOCTOR) | Block a time slot |

---

#### Medical Records — Prescriptions

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/prescriptions` | Bearer (DOCTOR) | Create prescription |
| GET | `/api/v1/prescriptions/{id}` | Bearer | Get by ID |
| GET | `/api/v1/prescriptions/my` | Bearer (PATIENT) | Get my prescriptions |
| GET | `/api/v1/prescriptions/doctor/my` | Bearer (DOCTOR) | Get prescriptions I wrote |
| PATCH | `/api/v1/prescriptions/{id}/cancel` | Bearer (DOCTOR) | Cancel prescription |

---

#### Medical Records — Lab Tests

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/tests` | Bearer (DOCTOR) | Order a lab test |
| GET | `/api/v1/tests/{id}` | Bearer | Get test by ID |
| GET | `/api/v1/tests/my` | Bearer (PATIENT) | Get my tests |
| GET | `/api/v1/tests/doctor/my` | Bearer (DOCTOR) | Get tests I ordered |
| PUT | `/api/v1/tests/{id}/result` | Bearer | Upload test result |

**Order Lab Test:**
```json
POST /api/v1/tests
Authorization: Bearer {{doctor_token}}
{
  "patientId": 1,
  "testName": "Complete Blood Count",
  "testType": "HAEMATOLOGY"
}
```

---

#### Billing

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/bills` | Bearer (DOCTOR/ADMIN) | Create bill |
| GET | `/api/v1/bills/{id}` | Bearer | Get bill by ID |
| GET | `/api/v1/bills/my` | Bearer (PATIENT) | Get my bills |
| GET | `/api/v1/bills/patient/{patientId}` | Bearer (ADMIN) | Get bills for patient |
| PATCH | `/api/v1/bills/{id}/cancel` | Bearer (ADMIN) | Cancel bill |

**Create Bill:**
```json
POST /api/v1/bills
Authorization: Bearer {{doctor_token}}
{
  "patientId": 1,
  "items": [
    {
      "itemType": "CONSULTATION",
      "description": "Cardiology consultation",
      "quantity": 1,
      "unitPrice": 150.00
    }
  ]
}
```

---

#### Payments

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/payments` | Bearer | Process payment |
| GET | `/api/v1/payments/{id}` | Bearer | Get payment by ID |
| GET | `/api/v1/payments/bill/{billId}` | Bearer | Get payments for a bill |
| GET | `/api/v1/payments/my` | Bearer (PATIENT) | Get my payment history |

**Process Payment:**
```json
POST /api/v1/payments
Authorization: Bearer {{patient_token}}
{
  "billId": 1,
  "amount": 150.00,
  "paymentMethod": "CARD"
}
```

---

#### Notifications

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/v1/notifications/my` | Bearer | Get my notifications |
| GET | `/api/v1/notifications/my/unread-count` | Bearer | Get unread count |
| GET | `/api/v1/notifications/user/{userId}` | Bearer (ADMIN) | Get notifications for user |

---

## Dev Tool URLs

| Tool | URL | Credentials |
|------|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | `eureka` / `eureka-secret` |
| Kafka UI | http://localhost:8090 | None |
| Redis Commander | http://localhost:8091 | None |
| User Service Swagger | http://localhost:8081/swagger-ui.html | None |
| Appointment Swagger | http://localhost:8082/swagger-ui.html | None |
| Medical Records Swagger | http://localhost:8083/swagger-ui.html | None |
| Billing Swagger | http://localhost:8084/swagger-ui.html | None |
| Notification Swagger | http://localhost:8085/swagger-ui.html | None |

---

## Running Tests

Run all unit tests for a specific service:
```bash
cd /path/to/hms/user-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

Run tests for all services from the project root:
```bash
cd /path/to/hms
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

---

## Stopping Everything

**Stop all Spring Boot services:**
Press `Ctrl+C` in each terminal running a service.

**Stop Docker containers:**
```bash
cd /path/to/hms
docker-compose down
```

**Stop containers and remove all data (full reset):**
```bash
docker-compose down -v
```

> **Warning:** `docker-compose down -v` deletes all database data. Use only when you want a clean slate.

---

## Troubleshooting

### `docker-credential-desktop: executable file not found`
```bash
echo '{"auths":{}}' > ~/.docker/config.json
```

### `Cannot connect to Docker daemon`
```bash
colima start
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
```

### `TypeTag :: UNKNOWN` (Maven compiler error)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

### Service fails with `Schema-validation: missing column`
The DB schema is out of sync. Recreate containers with a fresh volume:
```bash
docker-compose down -v
docker-compose up -d
```
Wait for all containers to be healthy, then restart services.

### Service not appearing in Eureka
- Ensure Eureka Server is fully started before starting other services
- Check the service's own health: `curl http://localhost:{port}/actuator/health`
- Look for connection errors in the service startup logs

### `401 Unauthorized` from Eureka dashboard
Use the credentials: `eureka` / `eureka-secret`
URL: `http://eureka:eureka-secret@localhost:8761`

### Port already in use
```bash
# Find and kill the process on a port (e.g., 8081)
lsof -ti:8081 | xargs kill -9
```
