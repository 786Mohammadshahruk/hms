# Hospital Management System (HMS)

A production-style, microservices-based Hospital Management System built with **Spring Boot 3.1.5**, **Spring Cloud**, and **Java 21**. The system handles patient registration, doctor scheduling, appointments, prescriptions, lab tests, billing, payments, and real-time notifications — all coordinated through an API Gateway with JWT authentication.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Services](#services)
- [Infrastructure](#infrastructure)
- [Database Design](#database-design)
- [Kafka Event Flow](#kafka-event-flow)
- [Security Model](#security-model)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Developer Tools](#developer-tools)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
  Client (Postman / Browser)
          │
          ▼
  ┌───────────────┐
  │  API Gateway  │  :8080
  │               │
  │  JWT Filter   │  ← validates token, injects X-User-* headers
  │  Internal     │    + X-Internal-Secret on every forwarded request
  │  Secret Stamp │
  └───────┬───────┘
          │  (lb://service-name via Eureka)
          ├──► User Service         :8081
          ├──► Appointment Service  :8082
          ├──► Medical Records      :8083
          ├──► Billing Service      :8084
          └──► Notification Service :8085

  All services register with:
  ┌───────────────┐
  │ Eureka Server │  :8761  (service discovery + load balancing)
  └───────────────┘

  Local Infrastructure (installed natively):
  ┌─────────────────────────────────────────────┐
  │  PostgreSQL :5432   Redis :6379              │
  │  Zookeeper  :2181   Kafka :9092              │
  └─────────────────────────────────────────────┘
```

**Key design principles:**
- **Single API entry point** via API Gateway — clients never call services directly (direct calls return `403 Forbidden`)
- **JWT tokens** issued by User Service, validated at the gateway only
- **InternalAuthFilter** in each service — trusts user identity from gateway-injected headers (`X-User-Id`, `X-User-Email`, `X-User-Role`), rejects requests missing the `X-Internal-Secret` stamp
- **Schema-per-service** database isolation — each service owns its PostgreSQL schema
- **Kafka** for async, decoupled inter-service communication (events, not REST calls)
- **Redis** for JWT blacklisting (logout) and response caching
- **Eureka** for service discovery and client-side load balancing
- **MDC logging** — every request carries `requestId`, `userId`, `userRole`, `method`, `path` in all log lines

---

## Technology Stack

| Category | Technology |
|----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.1.5 |
| Service Discovery | Spring Cloud Netflix Eureka 2022.0.4 |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Security | Spring Security + JWT (JJWT 0.12.5) |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Caching | Redis 7 (via Spring Cache + Lettuce) |
| Messaging | Apache Kafka |
| Mapping | MapStruct 1.5.5 |
| Boilerplate | Lombok 1.18.30 |
| API Docs | SpringDoc OpenAPI 2.2.0 (Swagger UI) |
| Build | Maven 3.8+ |
| Logging | SLF4J + Logback + MDC |
| Testing | JUnit 5 + Mockito + MockMvc |

---

## Services

### 1. Eureka Server — `:8761`
Service registry. All microservices register here on startup. The API Gateway discovers service instances through Eureka for load-balanced routing.

- Dashboard: http://localhost:8761 (login: `eureka` / `eureka-secret`)

---

### 2. API Gateway — `:8080`
Single entry point for all client requests. Responsibilities:
- **JWT validation** via `AuthenticationFilter` — rejects requests with missing or invalid tokens (except `/api/v1/auth/**`)
- **Header injection** — forwards `X-User-Id`, `X-User-Email`, `X-User-Role` to services
- **Internal secret stamping** — adds `X-Internal-Secret` via `CorrelationIdFilter` on every forwarded request
- **Correlation ID** — generates `X-Correlation-Id` for distributed tracing across services
- **Route forwarding** to downstream services via Eureka load balancer (`lb://service-name`)

| Route | Downstream Service | Auth Required |
|-------|--------------------|---------------|
| `/api/v1/auth/**` | user-service | No |
| `/api/v1/users/**` | user-service | Yes |
| `/api/v1/appointments/**`, `/api/v1/schedules/**` | appointment-service | Yes |
| `/api/v1/prescriptions/**`, `/api/v1/tests/**` | medical-records-service | Yes |
| `/api/v1/bills/**`, `/api/v1/payments/**` | billing-service | Yes |
| `/api/v1/notifications/**` | notification-service | Yes |

---

### 3. User Service — `:8081`
Handles identity, authentication, and user profiles. The **only service that issues JWT tokens**.

**Responsibilities:**
- Public registration (PATIENT role only)
- Admin-controlled creation of DOCTOR, ADMIN, RECEPTIONIST accounts
- JWT access token + refresh token management
- Token blacklisting on logout (stored in Redis)
- Doctor profile management (specialization, license, availability)
- Patient profile management
- Role management and account activation/deactivation

**DB Schema:** `hms_user`

---

### 4. Appointment Service — `:8082`
Manages doctor availability and patient appointments.

**Responsibilities:**
- Doctor sets weekly schedules (day-of-week, start/end time, slot duration)
- Doctors can block specific time slots
- Patients book appointments — conflict detection prevents double-booking
- Appointment lifecycle: `SCHEDULED → IN_PROGRESS → COMPLETED / CANCELLED / NO_SHOW`
- Publishes Kafka events: `appointment.booked`, `appointment.cancelled`

**DB Schema:** `hms_appointment`

---

### 5. Medical Records Service — `:8083`
Handles prescriptions and laboratory tests.

**Responsibilities:**
- Doctors create prescriptions with medication line items
- Doctors order lab tests (blood work, imaging, etc.)
- Lab staff upload test results and mark normal/abnormal
- Prescription lifecycle: `ACTIVE → DISPENSED / CANCELLED / EXPIRED`
- Test lifecycle: `ORDERED → IN_PROGRESS → COMPLETED / CANCELLED`
- Publishes Kafka events: `prescription.created`, `test.ordered`, `test.result.uploaded`

**DB Schema:** `hms_medical`

---

### 6. Billing Service — `:8084`
Handles bill generation and payment processing.

**Responsibilities:**
- Doctors/admins generate itemised bills (consultation, test, procedure, medicine, bed charge)
- Bill lifecycle: `PENDING → PARTIAL → PAID / CANCELLED / OVERDUE`
- Dummy payment gateway (95% success rate, configurable)
- Supports partial payments — bill status moves to `PARTIAL` until fully paid
- Publishes Kafka events: `bill.generated`, `payment.success`, `payment.failed`, `payment.overdue`

**DB Schema:** `hms_billing`

---

### 7. Notification Service — `:8085`
Pure Kafka consumer — listens to events from all other services and persists notifications.

**Responsibilities:**
- Consumes all domain events and creates in-app notifications
- Dummy email + SMS mode (logs to console — no real SMTP/SMS needed)
- Patients can read their own notifications
- Admins can query notifications for any user
- Unread count endpoint for badge display

**Consumed Kafka topics:**
`user.registered`, `appointment.booked`, `appointment.cancelled`,
`prescription.created`, `test.result.uploaded`, `bill.generated`, `payment.success`, `payment.overdue`

**DB Schema:** `hms_notification`

---

## Infrastructure

All infrastructure components run locally (no Docker required).

### Service Ports Summary

| Component | Port |
|-----------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Zookeeper | 2181 |
| Kafka | 9092 |
| Eureka Server | 8761 |
| API Gateway | 8080 |
| User Service | 8081 |
| Appointment Service | 8082 |
| Medical Records Service | 8083 |
| Billing Service | 8084 |
| Notification Service | 8085 |

---

## Database Design

Single PostgreSQL instance (`hms_db`) with **schema-per-service** isolation:

| Schema | Owner Service | Key Tables |
|--------|--------------|------------|
| `hms_user` | User Service | `users`, `doctor_profiles`, `patient_profiles`, `refresh_tokens` |
| `hms_appointment` | Appointment Service | `appointments`, `doctor_schedules`, `blocked_slots` |
| `hms_medical` | Medical Records Service | `prescriptions`, `prescription_items`, `medical_tests` |
| `hms_billing` | Billing Service | `bills`, `bill_items`, `payments` |
| `hms_notification` | Notification Service | `notifications` |

Each service's Hibernate is configured with `default_schema` so it can only see its own tables. Schemas are initialised from `scripts/init-schemas.sql` on first run, then Hibernate auto-updates them (`ddl-auto: update`).

---

## Kafka Event Flow

```
User Service ───────► user.registered ─────────────────────► Notification Service
                                                               (welcome notification)

Appointment Service ► appointment.booked ──────────────────► Notification Service
                    ► appointment.cancelled ────────────────► Notification Service

Medical Records ────► prescription.created ────────────────► Notification Service
                    ► test.result.uploaded ─────────────────► Notification Service

Billing Service ────► bill.generated ──────────────────────► Notification Service
                    ► payment.success ──────────────────────► Notification Service
                    ► payment.failed ───────────────────────► Notification Service
                    ► payment.overdue ──────────────────────► Notification Service
```

---

## Security Model

```
Client
  │
  │  JWT (Bearer token)
  ▼
API Gateway  ──► AuthenticationFilter validates JWT
  │              CorrelationIdFilter stamps X-Internal-Secret + X-Correlation-Id
  │              Injects: X-User-Id, X-User-Email, X-User-Role
  ▼
Downstream Service
  │
  InternalAuthFilter
    ├─ X-Internal-Secret missing or wrong → 403 "Access via API Gateway only"
    └─ Secret valid → build SecurityContext from injected headers (no DB lookup)
```

- Calling any service directly on its port (`:8081`–`:8085`) without the gateway returns `403`
- JWT is validated **once** at the gateway — services trust the gateway-injected headers
- Refresh tokens are stored in the database; each use rotates the token pair

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java JDK | 21 | Must be JDK 21, not JRE |
| Apache Maven | 3.8+ | Ensure `JAVA_HOME` points to JDK 21 |
| PostgreSQL | 16 | Running locally on port 5432 |
| Redis | 7 | Running locally on port 6379 |
| Apache Kafka | Any | With Zookeeper on ports 9092 / 2181 |

---

## Getting Started

### Step 1 — Set Java 21

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version   # must show openjdk 21
```

To make permanent:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
```

---

### Step 2 — Start Infrastructure

Start Redis, Zookeeper, and Kafka (see `STARTUP_GUIDE.md` for platform-specific commands). PostgreSQL should already be running as a service.

---

### Step 3 — One-Time Database Setup

Run the schema initialisation script in pgAdmin or psql:

```bash
psql -U hms_user -d hms_db -f scripts/init-schemas.sql
```

Or open `scripts/init-schemas.sql` in pgAdmin's Query Tool and execute it.

---

### Step 4 — Start Services in Order

> Each service needs its own terminal. Always start **Eureka first**, **API Gateway last**.

```bash
# Terminal 1 — Eureka Server (wait until fully started before proceeding)
cd eureka-server && mvn spring-boot:run

# Terminal 2
cd user-service && mvn spring-boot:run

# Terminal 3
cd appointment-service && mvn spring-boot:run

# Terminal 4
cd medical-records-service && mvn spring-boot:run

# Terminal 5
cd billing-service && mvn spring-boot:run

# Terminal 6
cd notification-service && mvn spring-boot:run

# Terminal 7 — API Gateway (start last)
cd api-gateway && mvn spring-boot:run
```

---

### Step 5 — Verify Everything Is Up

**Eureka Dashboard:** http://localhost:8761 (`eureka` / `eureka-secret`)

You should see all 5 services registered:

```
APPOINTMENT-SERVICE      — 1 instance UP
BILLING-SERVICE          — 1 instance UP
MEDICAL-RECORDS-SERVICE  — 1 instance UP
NOTIFICATION-SERVICE     — 1 instance UP
USER-SERVICE             — 1 instance UP
```

---

## API Reference

All requests go through **`http://localhost:8080`** (API Gateway). Direct calls to individual service ports are blocked.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Register a new patient |
| `POST` | `/api/v1/auth/login` | None | Login — returns `accessToken` + `refreshToken` |
| `POST` | `/api/v1/auth/refresh` | None | Get new access token using refresh token |
| `POST` | `/api/v1/auth/logout` | Bearer | Invalidate session |

**Register:**
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

> Response contains `data.accessToken` — use as `Authorization: Bearer <token>` on all protected requests.

---

### Users

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `GET` | `/api/v1/users/me` | All | Get own profile |
| `PUT` | `/api/v1/users/me` | All | Update own profile |
| `PATCH` | `/api/v1/users/me/password` | All | Change own password |
| `GET` | `/api/v1/users/{id}` | All | Get user by ID |
| `GET` | `/api/v1/users/doctors` | All | List all doctors (paginated, filterable) |
| `GET` | `/api/v1/users` | ADMIN | List all users (paginated) |
| `POST` | `/api/v1/users/admin/create` | ADMIN | Create any user (doctor, admin, etc.) |
| `PATCH` | `/api/v1/users/admin/{id}/role` | ADMIN | Change user's role |
| `PATCH` | `/api/v1/users/admin/{id}/deactivate` | ADMIN | Deactivate user account |
| `PATCH` | `/api/v1/users/admin/{id}/activate` | ADMIN | Activate user account |
| `POST` | `/api/v1/users/admin/{id}/reset-password` | ADMIN | Reset user's password |

**Admin — Create Doctor:**
```json
POST /api/v1/users/admin/create
Authorization: Bearer <admin_token>
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

### Appointments

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/appointments` | PATIENT | Book an appointment |
| `GET` | `/api/v1/appointments/my` | All | Get own appointments |
| `GET` | `/api/v1/appointments/{id}` | All | Get appointment by ID |
| `PATCH` | `/api/v1/appointments/{id}/cancel` | All | Cancel an appointment |
| `PATCH` | `/api/v1/appointments/{id}/status` | DOCTOR | Update appointment status |

**Book Appointment:**
```json
POST /api/v1/appointments
Authorization: Bearer <patient_token>
{
  "doctorId": 2,
  "appointmentDate": "2026-04-10",
  "startTime": "10:00",
  "type": "CONSULTATION",
  "reason": "General checkup"
}
```

---

### Doctor Schedules

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/schedules` | DOCTOR | Set weekly availability |
| `GET` | `/api/v1/schedules/doctor/{doctorId}` | All | Get doctor's schedules |
| `GET` | `/api/v1/schedules/doctor/{doctorId}/slots?date=YYYY-MM-DD` | All | Get available time slots for a date |
| `POST` | `/api/v1/schedules/{id}/block` | DOCTOR | Block a time slot |

---

### Prescriptions

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/prescriptions` | DOCTOR | Create prescription |
| `GET` | `/api/v1/prescriptions/{id}` | All | Get prescription by ID |
| `GET` | `/api/v1/prescriptions/my` | PATIENT | Get own prescriptions |
| `GET` | `/api/v1/prescriptions/doctor/my` | DOCTOR | Get prescriptions I issued |
| `PATCH` | `/api/v1/prescriptions/{id}/cancel` | DOCTOR | Cancel prescription |

---

### Lab Tests

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/tests` | DOCTOR | Order a lab test |
| `GET` | `/api/v1/tests/{id}` | All | Get test by ID |
| `GET` | `/api/v1/tests/my` | PATIENT | Get own test history |
| `GET` | `/api/v1/tests/doctor/my` | DOCTOR | Get tests I ordered |
| `PUT` | `/api/v1/tests/{id}/result` | All | Upload test result |

---

### Bills

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/bills` | DOCTOR, ADMIN | Generate a bill |
| `GET` | `/api/v1/bills/{id}` | All | Get bill by ID |
| `GET` | `/api/v1/bills/my` | PATIENT | Get own bills |
| `GET` | `/api/v1/bills/patient/{patientId}` | ADMIN | Get bills for a patient |
| `PATCH` | `/api/v1/bills/{id}/cancel` | ADMIN | Cancel a bill |

> Item types: `CONSULTATION`, `TEST`, `PROCEDURE`, `MEDICINE`, `BED_CHARGE`, `OTHER`

---

### Payments

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/api/v1/payments` | PATIENT, ADMIN, RECEPTIONIST | Process payment |
| `GET` | `/api/v1/payments/{id}` | All | Get payment by ID |
| `GET` | `/api/v1/payments/bill/{billId}` | All | Get all payments for a bill |
| `GET` | `/api/v1/payments/my` | PATIENT | Get own payment history |

> Payment methods: `CASH`, `CARD`, `UPI`, `INSURANCE`, `ONLINE`, `OTHER`
> The dummy gateway has a **95% success rate** — occasional `FAILED` responses are by design.

---

### Notifications

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `GET` | `/api/v1/notifications/my` | All | Get own notifications (paginated) |
| `GET` | `/api/v1/notifications/my/unread-count` | All | Get unread notification count |
| `GET` | `/api/v1/notifications/user/{userId}` | ADMIN | Get notifications for any user |

---

### Standard Response Envelope

**Success:**
```json
{
  "success": true,
  "message": "Operation description",
  "data": { ... },
  "timestamp": "2026-04-02T10:00:00"
}
```

**Error:**
```json
{
  "success": false,
  "message": "Descriptive error message",
  "data": null,
  "timestamp": "2026-04-02T10:00:00"
}
```

---

## Developer Tools

| Tool | URL | Credentials |
|------|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | `eureka` / `eureka-secret` |
| User Service — Swagger | http://localhost:8081/swagger-ui.html | None |
| Appointment Service — Swagger | http://localhost:8082/swagger-ui.html | None |
| Medical Records — Swagger | http://localhost:8083/swagger-ui.html | None |
| Billing Service — Swagger | http://localhost:8084/swagger-ui.html | None |
| Notification Service — Swagger | http://localhost:8085/swagger-ui.html | None |

> **Note:** Swagger UIs are accessible directly on service ports for development convenience. All production API calls must go through the gateway on `:8080`.

---

## Running Tests

**Single service:**
```bash
cd user-service
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

**All services from root:**
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

| Service | Test Classes |
|---------|-------------|
| user-service | `AuthServiceTest`, `UserServiceTest`, `AuthControllerTest`, `UserControllerTest` |
| appointment-service | `DoctorScheduleServiceTest`, `AppointmentControllerTest`, `DoctorScheduleControllerTest` |
| medical-records-service | `MedicalTestServiceTest`, `PrescriptionControllerTest`, `MedicalTestControllerTest` |
| billing-service | `PaymentServiceTest`, `BillControllerTest`, `PaymentControllerTest` |
| notification-service | `NotificationControllerTest` |

---

## Troubleshooting

### `TypeTag :: UNKNOWN` during Maven build
Maven is not using Java 21:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

### Service fails with `Schema-validation: missing column`
Database schema is out of sync with entities. Re-run the init script:
```bash
psql -U hms_user -d hms_db -f scripts/init-schemas.sql
```

### Service fails with `Connection refused to localhost:5432`
PostgreSQL is not running. Start it via your system's service manager.

### Service fails with `Connection refused to localhost:9092`
Kafka is not running. Start Zookeeper first, then Kafka.

### Service fails with `Connection refused to localhost:6379`
Redis is not running. Start Redis with `redis-server`.

### Service not registering with Eureka
- Confirm Eureka Server started fully before other services
- Check service health: `curl http://localhost:{port}/actuator/health`
- Services take 30-60 seconds to register after startup

### `401 Unauthorized` on Eureka dashboard
Use basic-auth in the URL: `http://eureka:eureka-secret@localhost:8761`

### `403 Forbidden` when calling a service
You are calling a service directly on its port instead of through the gateway. All API calls must go through `http://localhost:8080`.

### Port already in use
```bash
lsof -ti:8081 | xargs kill -9   # replace 8081 with the blocked port
```

### Stopping Everything

```bash
# Stop Spring Boot services — Ctrl+C in each terminal

# Stop Kafka, Zookeeper, Redis — Ctrl+C in their respective terminals
```
