# HMS Application — Complete Startup Guide

## Overview

The application has **2 layers** that must be started in order:

```
Layer 1 — Infrastructure   : PostgreSQL + Redis + Kafka + Zookeeper
Layer 2 — Spring Services  : Eureka → 5 Microservices → API Gateway
```

**Golden Rule:**
- Always start infrastructure FIRST
- Always start Eureka BEFORE any microservice
- Always start API Gateway LAST

---

## Ports Reference

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

## STEP 1 — Start Infrastructure

### Option A — Using Docker

Open CMD and run:

```cmd
cd C:\path\to\hms
docker-compose up -d
```

Verify all containers are running:

```cmd
docker ps
```

All containers should show `healthy` status.

---

### Option B — Using Locally Installed Tools (No Docker)

You need **3 separate CMD windows**.

**CMD 1 — Start Redis**
```cmd
redis-server
```
Wait for: `Ready to accept connections on port 6379`

**CMD 2 — Start Zookeeper**
```cmd
cd C:\kafka
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
```
Wait for: `binding to port 0.0.0.0/0.0.0.0:2181`

**CMD 3 — Start Kafka**
```cmd
cd C:\kafka
.\bin\windows\kafka-server-start.bat .\config\server.properties
```
Wait for: `started (kafka.server.KafkaServer)`

> PostgreSQL runs automatically as a Windows Service after installation.
> Redis also runs as a Windows Service — CMD 1 above is optional if service is already running.

---

## STEP 2 — One Time Database Setup

> Skip this if you have already done this before.

**Open pgAdmin** and run these steps:

### 2.1 Create User
- Right-click **Login/Group Roles** → Create → Login/Group Role
- General tab → Name: `hms_user`
- Definition tab → Password: `hms_pass`
- Privileges tab → Turn ON: Can login, Create databases
- Click Save

### 2.2 Create Database
- Right-click **Databases** → Create → Database
- Database: `hms_db`
- Owner: `hms_user`
- Click Save

### 2.3 Run Schema Script
- Click on `hms_db` to select it
- Click **Query Tool** (or press `Alt + Shift + Q`)
- Click folder icon → Open file: `C:\path\to\hms\scripts\init-schemas.sql`
- Press **F5** to execute

You should see: `Query returned successfully`

### 2.4 Verify Schemas Created
In pgAdmin left panel under `hms_db → Schemas` you should see:

```
hms_user         → users, doctor_profiles, patient_profiles, refresh_tokens
hms_appointment  → appointments, doctor_schedules, blocked_slots
hms_medical      → prescriptions, prescription_items, medical_tests
hms_billing      → bills, bill_items, payments
hms_notification → notifications
```

---

## STEP 3 — Start Spring Boot Services

Open a **separate CMD window for each service**.
Set Java 21 in each CMD before running:

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
```

---

### 3.1 — Eureka Server (START FIRST — wait until ready)

```cmd
cd C:\path\to\hms\eureka-server
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started EurekaServerApplication in X.XXX seconds
```

**Verify:** Open browser → http://localhost:8761
Login: `eureka` / `eureka-secret`
You should see the Eureka dashboard.

---

### 3.2 — User Service

```cmd
cd C:\path\to\hms\user-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started UserServiceApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8081/actuator/health
```
Expected: `{"status":"UP"}`

---

### 3.3 — Appointment Service

```cmd
cd C:\path\to\hms\appointment-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started AppointmentServiceApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8082/actuator/health
```
Expected: `{"status":"UP"}`

---

### 3.4 — Medical Records Service

```cmd
cd C:\path\to\hms\medical-records-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started MedicalRecordsServiceApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8083/actuator/health
```
Expected: `{"status":"UP"}`

---

### 3.5 — Billing Service

```cmd
cd C:\path\to\hms\billing-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started BillingServiceApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8084/actuator/health
```
Expected: `{"status":"UP"}`

---

### 3.6 — Notification Service

```cmd
cd C:\path\to\hms\notification-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started NotificationServiceApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8085/actuator/health
```
Expected: `{"status":"UP"}`

---

### 3.7 — API Gateway (START LAST)

```cmd
cd C:\path\to\hms\api-gateway
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
mvn spring-boot:run
```

**Wait until you see:**
```
Started ApiGatewayApplication in X.XXX seconds
```

**Verify:**
```cmd
curl http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`

---

## STEP 4 — Final Verification

### 4.1 Check All Services Health

Run all at once:

```cmd
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8080/actuator/health
```

All should return `{"status":"UP"}`

### 4.2 Check Eureka Dashboard

Open browser: http://localhost:8761
Login: `eureka` / `eureka-secret`

Under **"Instances currently registered with Eureka"** you must see all 5:

```
✅ USER-SERVICE             1 instance UP
✅ APPOINTMENT-SERVICE      1 instance UP
✅ MEDICAL-RECORDS-SERVICE  1 instance UP
✅ BILLING-SERVICE          1 instance UP
✅ NOTIFICATION-SERVICE     1 instance UP
```

### 4.3 Test via API Gateway

```cmd
curl -X POST http://localhost:8080/api/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"test@hms.com\",\"password\":\"Test1@pass\",\"phone\":\"+1234567890\"}"
```

If you get back `{"success":true,...}` — everything is working correctly.

---

## Complete Startup Order Summary

```
START ORDER                          CMD WINDOW
─────────────────────────────────────────────────
[Infrastructure]
  1. PostgreSQL          (auto / service)
  2. Redis               CMD 1
  3. Zookeeper           CMD 2
  4. Kafka               CMD 3

[Spring Boot Services]
  5. Eureka Server       CMD 4   ← wait until ready
  6. User Service        CMD 5
  7. Appointment Service CMD 6
  8. Medical Records     CMD 7
  9. Billing Service     CMD 8
 10. Notification Service CMD 9
 11. API Gateway         CMD 10  ← always last
```

---

## Stopping Everything

```cmd
# Stop Spring Boot services
Ctrl+C in each CMD window (CMD 4 to CMD 10)

# Stop Kafka
Ctrl+C in CMD 3

# Stop Zookeeper
Ctrl+C in CMD 2

# Stop Redis
Ctrl+C in CMD 1

# Stop Docker containers (if using Docker)
docker-compose down
```

---

## Troubleshooting

### Service fails to start — `Connection refused to localhost:8761`
Eureka is not running yet. Start Eureka first and wait until fully started.

### Service fails to start — `Connection refused to localhost:5432`
PostgreSQL is not running.
- Docker: `docker-compose up -d`
- Local: Start PostgreSQL service from Windows Services

### Service fails to start — `Schema-validation: missing column`
Tables are missing or out of sync. Re-run `init-schemas.sql` from pgAdmin.

### Service fails to start — `Connection refused to localhost:9092`
Kafka is not running. Start Zookeeper first, then Kafka.

### Service not showing in Eureka
- Check the service started without errors
- Check health: `curl http://localhost:{port}/actuator/health`
- Services take 30-60 seconds to register after startup

### `401 Unauthorized` from Eureka dashboard
Use credentials: `eureka` / `eureka-secret`

### Port already in use
```cmd
netstat -ano | findstr :8081
taskkill /PID <PID_NUMBER> /F
```
Replace `8081` with the blocked port.

### `JAVA_HOME` not set correctly
```cmd
java -version
```
Must show `openjdk 21`. If not:
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot
```
