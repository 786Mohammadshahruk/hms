# HMS — Complete API Calling Sequence by Journey

> **Base URLs**
> | Service | URL |
> |---|---|
> | user-service | `http://localhost:8081` |
> | appointment-service | `http://localhost:8082` |
> | medical-records-service | `http://localhost:8083` |
> | billing-service | `http://localhost:8084` |
> | notification-service | `http://localhost:8085` |

> **Token Variables used throughout this document**
> | Variable | How to get |
> |---|---|
> | `<ADMIN_TOKEN>` | Login with admin@hms.com |
> | `<DOCTOR_TOKEN>` | Login with john.doe@hms.com |
> | `<CASHIER_TOKEN>` | Login with sara@hms.com |
> | `<PATIENT_TOKEN>` | Login with alice@hms.com |

---

## JOURNEY 0 — Initial Setup (Admin)

> Run this once before anything else.

### Step 1 — Register Admin

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Super",
    "lastName": "Admin",
    "email": "admin@hms.com",
    "password": "Admin@123",
    "role": "ADMIN"
  }'
```

### Step 2 — Admin Login → save ADMIN_TOKEN

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@hms.com", "password": "Admin@123"}'
```

### Step 3 — Admin creates a Doctor

```bash
curl -X POST http://localhost:8081/api/v1/users/admin/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@hms.com",
    "password": "Doctor@123",
    "role": "DOCTOR",
    "specialization": "Cardiology",
    "department": "Cardiology",
    "licenseNumber": "LIC-001",
    "consultationFee": 500.00
  }'
```

> Save `doctorId` from response (e.g. `2`)

### Step 4 — Admin creates a Cashier

```bash
curl -X POST http://localhost:8081/api/v1/users/admin/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "firstName": "Sara",
    "lastName": "Khan",
    "email": "sara@hms.com",
    "password": "Cashier@123",
    "role": "CASHIER"
  }'
```

---

## JOURNEY 1 — Doctor Sets Availability

> Doctor must set schedule before patients can book.

### Step 1 — Doctor Login → save DOCTOR_TOKEN

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "john.doe@hms.com", "password": "Doctor@123"}'
```

### Step 2 — Doctor sets schedule for each working day

```bash
curl -X POST http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00", "slotDurationMinutes": 30}'

curl -X POST http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "17:00", "slotDurationMinutes": 30}'

curl -X POST http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "17:00", "slotDurationMinutes": 30}'

curl -X POST http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "17:00", "slotDurationMinutes": 30}'

curl -X POST http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "13:00", "slotDurationMinutes": 30}'
```

### Step 3 — Doctor views their schedule

```bash
curl -X GET http://localhost:8082/api/v1/schedules/doctor/2 \
  -H "Authorization: Bearer <DOCTOR_TOKEN>"
```

### Step 4 — Doctor blocks a slot (leave/emergency)

```bash
curl -X POST http://localhost:8082/api/v1/schedules/doctor/2/block \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{
    "date": "2026-04-07",
    "startTime": "14:00",
    "endTime": "16:00",
    "reason": "Personal leave"
  }'
```

---

## JOURNEY 2 — Patient Registers and Books Appointment

### Step 1 — Patient registers
> 🔔 **Notification fired:** Welcome to HMS email

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Smith",
    "email": "alice@hms.com",
    "password": "Patient@123",
    "role": "PATIENT"
  }'
```

> Save `patientId` from response (e.g. `3`)

### Step 2 — Patient login → save PATIENT_TOKEN

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@hms.com", "password": "Patient@123"}'
```

### Step 3 — Patient views their profile

```bash
curl -X GET http://localhost:8081/api/v1/users/me \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

### Step 4 — Patient browses doctors (no token required)

```bash
curl -X GET "http://localhost:8081/api/v1/users/doctors?specialization=cardio"
```

### Step 5 — Patient checks available slots for a date

```bash
curl -X GET "http://localhost:8082/api/v1/schedules/doctor/2/slots?date=2026-04-07"
```

### Step 6 — Patient books appointment
> 🔔 **Notification fired:** Appointment confirmation email

```bash
curl -X POST http://localhost:8082/api/v1/appointments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -d '{
    "doctorId": 2,
    "appointmentDate": "2026-04-07",
    "startTime": "10:00",
    "type": "CONSULTATION",
    "reason": "Chest pain and shortness of breath"
  }'
```

> Save `appointmentId` from response (e.g. `1`)

### Step 7 — Patient views their appointments

```bash
curl -X GET http://localhost:8082/api/v1/appointments/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

### Step 8 — Patient checks notifications

```bash
curl -X GET http://localhost:8085/api/v1/notifications/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

### Step 9 — Patient checks unread notification count

```bash
curl -X GET http://localhost:8085/api/v1/notifications/my/unread-count \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

---

## JOURNEY 3 — Reschedule or Cancel Appointment

### Reschedule

#### Step 1 — Check available slots for new date

```bash
curl -X GET "http://localhost:8082/api/v1/schedules/doctor/2/slots?date=2026-04-08"
```

#### Step 2 — Reschedule appointment

```bash
curl -X PUT http://localhost:8082/api/v1/appointments/1/reschedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -d '{
    "newDate": "2026-04-08",
    "newStartTime": "11:00",
    "reason": "Conflict with work"
  }'
```

### Cancel

#### Step 3 — Cancel appointment
> 🔔 **Notification fired:** Appointment cancellation email

```bash
curl -X PUT http://localhost:8082/api/v1/appointments/1/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -d '{"reason": "Feeling better now"}'
```

---

## JOURNEY 4 — Day of Appointment (Doctor Side)

### Step 1 — Doctor views today's appointments

```bash
curl -X GET "http://localhost:8082/api/v1/appointments/doctor/2?date=2026-04-08" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>"
```

### Step 2 — Patient arrives, doctor marks IN_PROGRESS

```bash
curl -X PATCH "http://localhost:8082/api/v1/appointments/1/status?status=IN_PROGRESS" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>"
```

### Step 3 — Doctor writes prescription

```bash
curl -X POST http://localhost:8083/api/v1/prescriptions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{
    "patientId": 3,
    "appointmentId": 1,
    "diagnosis": "Hypertension Stage 1",
    "notes": "Reduce salt intake, exercise daily",
    "validUntil": "2026-07-08",
    "medicines": [
      {
        "medicineName": "Amlodipine",
        "dosage": "5mg",
        "frequency": "Once daily",
        "durationDays": 90,
        "instructions": "Take after breakfast"
      },
      {
        "medicineName": "Aspirin",
        "dosage": "75mg",
        "frequency": "Once daily",
        "durationDays": 90,
        "instructions": "Take after dinner"
      }
    ]
  }'
```

> Save `prescriptionId` from response (e.g. `1`)

### Step 4 — Doctor orders a lab test

```bash
curl -X POST http://localhost:8083/api/v1/tests \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>" \
  -d '{
    "patientId": 3,
    "prescriptionId": 1,
    "testName": "Complete Blood Count",
    "testType": "Blood Test"
  }'
```

> Save `testId` from response (e.g. `1`)

### Step 5 — Doctor marks appointment COMPLETED

```bash
curl -X PATCH "http://localhost:8082/api/v1/appointments/1/status?status=COMPLETED" \
  -H "Authorization: Bearer <DOCTOR_TOKEN>"
```

---

## JOURNEY 5 — Billing and Payment (Cashier Side)

### Step 1 — Cashier login → save CASHIER_TOKEN

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "sara@hms.com", "password": "Cashier@123"}'
```

### Step 2 — Cashier creates bill
> 🔔 **Notification fired:** New bill generated email

```bash
curl -X POST http://localhost:8084/api/v1/bills \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <CASHIER_TOKEN>" \
  -d '{
    "patientId": 3,
    "appointmentId": 1,
    "items": [
      {"description": "Consultation Fee", "quantity": 1, "unitPrice": 500.00},
      {"description": "Blood Test",       "quantity": 1, "unitPrice": 300.00},
      {"description": "ECG",              "quantity": 1, "unitPrice": 200.00}
    ],
    "discountAmount": 100.00,
    "taxAmount": 90.00,
    "dueDate": "2026-04-15",
    "description": "Post-consultation billing"
  }'
```

> Save `billId` from response (e.g. `1`)

### Step 3 — View the bill

```bash
curl -X GET http://localhost:8084/api/v1/bills/1 \
  -H "Authorization: Bearer <CASHIER_TOKEN>"
```

### Step 4 — Patient pays first installment
> 🔔 **Notification fired:** Payment receipt email
> Bill status → `PARTIAL`

```bash
curl -X POST http://localhost:8084/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -d '{
    "billId": 1,
    "amount": 500.00,
    "paymentMethod": "CARD"
  }'
```

### Step 5 — Patient pays remaining balance
> 🔔 **Notification fired:** Payment receipt email
> Bill status → `PAID`

```bash
curl -X POST http://localhost:8084/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <PATIENT_TOKEN>" \
  -d '{
    "billId": 1,
    "amount": 490.00,
    "paymentMethod": "UPI"
  }'
```

### Step 6 — View all payments for the bill

```bash
curl -X GET http://localhost:8084/api/v1/payments/bill/1 \
  -H "Authorization: Bearer <CASHIER_TOKEN>"
```

### Step 7 — Patient views their payment history

```bash
curl -X GET http://localhost:8084/api/v1/payments/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

---

## JOURNEY 6 — Lab Test Result Upload

### Step 1 — Admin uploads test result
> 🔔 **Notification fired:** Test result available email (ALERT if abnormal)

```bash
curl -X PUT http://localhost:8083/api/v1/tests/1/result \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "resultValue": "Hemoglobin: 9.2 g/dL (Low), WBC: 11000 (High)",
    "isAbnormal": true,
    "labNotes": "Possible anemia and infection. Recommend follow-up.",
    "resultDate": "2026-04-08T14:00:00Z"
  }'
```

### Step 2 — Patient views test results

```bash
curl -X GET http://localhost:8083/api/v1/tests/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

### Step 3 — Patient views prescriptions

```bash
curl -X GET http://localhost:8083/api/v1/prescriptions/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

---

## JOURNEY 7 — Patient Full History

```bash
# All appointments
curl -X GET http://localhost:8082/api/v1/appointments/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# Only completed appointments
curl -X GET "http://localhost:8082/api/v1/appointments/my?status=COMPLETED" \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# All bills
curl -X GET http://localhost:8084/api/v1/bills/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# All payments
curl -X GET http://localhost:8084/api/v1/payments/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# All prescriptions
curl -X GET http://localhost:8083/api/v1/prescriptions/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# All lab tests
curl -X GET http://localhost:8083/api/v1/tests/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"

# All notifications
curl -X GET http://localhost:8085/api/v1/notifications/my \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

---

## JOURNEY 8 — Admin Operations

```bash
# View all users
curl -X GET http://localhost:8081/api/v1/users \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Filter by role
curl -X GET "http://localhost:8081/api/v1/users?role=DOCTOR" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Filter by active status
curl -X GET "http://localhost:8081/api/v1/users?active=true" \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Deactivate a user
curl -X PATCH http://localhost:8081/api/v1/users/admin/3/deactivate \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Reactivate a user
curl -X PATCH http://localhost:8081/api/v1/users/admin/3/activate \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Reset a user's password
curl -X POST http://localhost:8081/api/v1/users/admin/3/reset-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{"newPassword": "NewPass@123"}'

# Change user role
curl -X PATCH http://localhost:8081/api/v1/users/admin/3/role \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{"role": "DOCTOR"}'

# View notifications for any user
curl -X GET http://localhost:8085/api/v1/notifications/user/3 \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

---

## JOURNEY 9 — Token Management

### Refresh access token (when it expires after 24h)

```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<REFRESH_TOKEN>"}'
```

### Logout (invalidates refresh token)

```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H "Authorization: Bearer <PATIENT_TOKEN>"
```

---

## Notification Trigger Summary

| Journey Step | Kafka Topic | Notification Message |
|---|---|---|
| Patient registers | `user.registered` | Welcome to HMS |
| Patient books appointment | `appointment.booked` | Appointment confirmation |
| Patient cancels appointment | `appointment.cancelled` | Cancellation with reason |
| Cashier creates bill | `bill.generated` | Bill amount and due date |
| Patient pays | `payment.success` | Payment receipt with transaction ID |
| Bill past due date (scheduler) | `payment.overdue` | Overdue payment reminder |
| Lab uploads normal result | `test.result.uploaded` | Result is available |
| Lab uploads abnormal result | `test.result.uploaded` | ALERT: Abnormal result |

---

## Appointment Status Lifecycle

```
SCHEDULED → IN_PROGRESS → COMPLETED
SCHEDULED → CANCELLED
SCHEDULED → NO_SHOW
```

## Bill Status Lifecycle

```
PENDING → PARTIAL → PAID
PENDING → CANCELLED
```

## Payment Status Values

```
SUCCESS   — Payment processed successfully
FAILED    — Payment gateway returned failure
PENDING   — Payment initiated but not yet processed
```

## Prescription Status Values

```
ACTIVE    — Valid and active
EXPIRED   — Past valid_until date
CANCELLED — Cancelled by doctor
```

## Medical Test Status Values

```
ORDERED      — Test ordered by doctor
IN_PROGRESS  — Sample collected, being processed
COMPLETED    — Result uploaded
CANCELLED    — Test cancelled
```
