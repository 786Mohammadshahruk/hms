-- ============================================================
--  HMS — Single Database, Five Schemas
--  One schema per microservice for logical isolation.
--  All services connect to: jdbc:postgresql://localhost:5432/hms_db
--  Each service sets its own search_path via spring.jpa.properties.hibernate.default_schema
-- ============================================================

-- ── Create schemas ─────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS hms_user;
CREATE SCHEMA IF NOT EXISTS hms_appointment;
CREATE SCHEMA IF NOT EXISTS hms_medical;
CREATE SCHEMA IF NOT EXISTS hms_billing;
CREATE SCHEMA IF NOT EXISTS hms_notification;

-- Grant all privileges on schemas to hms_user
GRANT ALL ON SCHEMA hms_user         TO hms_user;
GRANT ALL ON SCHEMA hms_appointment  TO hms_user;
GRANT ALL ON SCHEMA hms_medical      TO hms_user;
GRANT ALL ON SCHEMA hms_billing      TO hms_user;
GRANT ALL ON SCHEMA hms_notification TO hms_user;

-- ============================================================
--  hms_user schema — User Service tables
-- ============================================================
SET search_path TO hms_user;

CREATE TABLE IF NOT EXISTS hms_user.users (
    id                  BIGSERIAL       PRIMARY KEY,
    uuid                UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    phone               VARCHAR(20),
    password_hash       VARCHAR(255)    NOT NULL,
    role                VARCHAR(20)     NOT NULL CHECK (role IN ('PATIENT','DOCTOR','CASHIER','ADMIN')),
    gender              VARCHAR(10)     CHECK (gender IN ('MALE','FEMALE','OTHER')),
    date_of_birth       DATE,
    address             TEXT,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    is_email_verified   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_user.doctor_profiles (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL UNIQUE REFERENCES hms_user.users(id) ON DELETE CASCADE,
    specialization      VARCHAR(150)    NOT NULL,
    license_number      VARCHAR(100)    NOT NULL UNIQUE,
    experience_years    INT             CHECK (experience_years >= 0),
    department          VARCHAR(100),
    consultation_fee    NUMERIC(10,2)   NOT NULL DEFAULT 0,
    available_from      TIME,
    available_to        TIME,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_user.patient_profiles (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL UNIQUE REFERENCES hms_user.users(id) ON DELETE CASCADE,
    blood_group         VARCHAR(5),
    emergency_contact   VARCHAR(20),
    allergies           TEXT,
    chronic_conditions  TEXT,
    insurance_provider  VARCHAR(150),
    insurance_number    VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_user.refresh_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES hms_user.users(id) ON DELETE CASCADE,
    token       TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email   ON hms_user.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role    ON hms_user.users(role);
CREATE INDEX IF NOT EXISTS idx_users_uuid    ON hms_user.users(uuid);
CREATE INDEX IF NOT EXISTS idx_rt_token      ON hms_user.refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_rt_user_id    ON hms_user.refresh_tokens(user_id);

-- ============================================================
--  hms_appointment schema — Appointment Service tables
-- ============================================================

CREATE TABLE IF NOT EXISTS hms_appointment.appointments (
    id                  BIGSERIAL       PRIMARY KEY,
    uuid                UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    patient_id          BIGINT          NOT NULL,
    doctor_id           BIGINT          NOT NULL,
    appointment_date    DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED'
                                        CHECK (status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    type                VARCHAR(30)     NOT NULL DEFAULT 'CONSULTATION'
                                        CHECK (type IN ('CONSULTATION','FOLLOW_UP','EMERGENCY','PROCEDURE')),
    reason              TEXT,
    notes               TEXT,
    cancellation_reason TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_appointment.doctor_schedules (
    id              BIGSERIAL   PRIMARY KEY,
    doctor_id       BIGINT      NOT NULL,
    day_of_week     VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    slot_duration   INT         NOT NULL DEFAULT 30,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(doctor_id, day_of_week)
);

CREATE TABLE IF NOT EXISTS hms_appointment.blocked_slots (
    id              BIGSERIAL   PRIMARY KEY,
    doctor_id       BIGINT      NOT NULL,
    blocked_date    DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    reason          VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appt_patient  ON hms_appointment.appointments(patient_id);
CREATE INDEX IF NOT EXISTS idx_appt_doctor   ON hms_appointment.appointments(doctor_id);
CREATE INDEX IF NOT EXISTS idx_appt_date     ON hms_appointment.appointments(appointment_date);
CREATE INDEX IF NOT EXISTS idx_appt_status   ON hms_appointment.appointments(status);
CREATE INDEX IF NOT EXISTS idx_appt_uuid     ON hms_appointment.appointments(uuid);
CREATE INDEX IF NOT EXISTS idx_sched_doctor  ON hms_appointment.doctor_schedules(doctor_id);
CREATE INDEX IF NOT EXISTS idx_blocked_doc   ON hms_appointment.blocked_slots(doctor_id, blocked_date);

-- ============================================================
--  hms_medical schema — Medical Records Service tables
-- ============================================================

CREATE TABLE IF NOT EXISTS hms_medical.prescriptions (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    patient_id      BIGINT          NOT NULL,
    doctor_id       BIGINT          NOT NULL,
    appointment_id  BIGINT,
    diagnosis       TEXT            NOT NULL,
    notes           TEXT,
    valid_until     DATE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_medical.prescription_medicines (
    id                  BIGSERIAL       PRIMARY KEY,
    prescription_id     BIGINT          NOT NULL REFERENCES hms_medical.prescriptions(id) ON DELETE CASCADE,
    medicine_name       VARCHAR(200)    NOT NULL,
    dosage              VARCHAR(100)    NOT NULL,
    frequency           VARCHAR(100)    NOT NULL,
    duration_days       INT,
    instructions        TEXT,
    quantity            INT
);

CREATE TABLE IF NOT EXISTS hms_medical.medical_tests (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    patient_id      BIGINT          NOT NULL,
    doctor_id       BIGINT          NOT NULL,
    prescription_id BIGINT          REFERENCES hms_medical.prescriptions(id),
    test_name       VARCHAR(200)    NOT NULL,
    test_type       VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ORDERED'
                                    CHECK (status IN ('ORDERED','SAMPLE_COLLECTED','PROCESSING','COMPLETED','CANCELLED')),
    ordered_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    result_date     TIMESTAMPTZ,
    result_value    TEXT,
    is_abnormal     BOOLEAN         DEFAULT FALSE,
    lab_notes       TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_presc_patient ON hms_medical.prescriptions(patient_id);
CREATE INDEX IF NOT EXISTS idx_presc_doctor  ON hms_medical.prescriptions(doctor_id);
CREATE INDEX IF NOT EXISTS idx_presc_uuid    ON hms_medical.prescriptions(uuid);
CREATE INDEX IF NOT EXISTS idx_test_patient  ON hms_medical.medical_tests(patient_id);
CREATE INDEX IF NOT EXISTS idx_test_status   ON hms_medical.medical_tests(status);
CREATE INDEX IF NOT EXISTS idx_test_uuid     ON hms_medical.medical_tests(uuid);

-- ============================================================
--  hms_billing schema — Billing Service tables
-- ============================================================

CREATE TABLE IF NOT EXISTS hms_billing.bills (
    id                  BIGSERIAL       PRIMARY KEY,
    uuid                UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    bill_number         VARCHAR(50)     NOT NULL UNIQUE,
    patient_id          BIGINT          NOT NULL,
    appointment_id      BIGINT,
    total_amount        NUMERIC(12,2)   NOT NULL DEFAULT 0,
    paid_amount         NUMERIC(12,2)   NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(12,2)   NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(12,2)   NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                        CHECK (status IN ('PENDING','PARTIAL','PAID','OVERDUE','CANCELLED','REFUNDED')),
    due_date            DATE,
    insurance_provider  VARCHAR(150),
    insurance_claim_id  VARCHAR(100),
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_billing.bill_items (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         BIGINT          NOT NULL REFERENCES hms_billing.bills(id) ON DELETE CASCADE,
    item_type       VARCHAR(30)     NOT NULL CHECK (item_type IN ('CONSULTATION','TEST','PROCEDURE','MEDICINE','BED_CHARGE','OTHER')),
    description     VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,
    unit_price      NUMERIC(10,2)   NOT NULL,
    total_price     NUMERIC(10,2)   NOT NULL
);

CREATE TABLE IF NOT EXISTS hms_billing.payments (
    id                  BIGSERIAL       PRIMARY KEY,
    uuid                UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    bill_id             BIGINT          NOT NULL REFERENCES hms_billing.bills(id),
    amount              NUMERIC(12,2)   NOT NULL,
    payment_method      VARCHAR(30)     NOT NULL CHECK (payment_method IN ('CASH','CARD','UPI','INSURANCE','ONLINE','OTHER')),
    payment_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                        CHECK (payment_status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    transaction_ref     VARCHAR(200),
    gateway_response    TEXT,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bill_patient  ON hms_billing.bills(patient_id);
CREATE INDEX IF NOT EXISTS idx_bill_status   ON hms_billing.bills(status);
CREATE INDEX IF NOT EXISTS idx_bill_uuid     ON hms_billing.bills(uuid);
CREATE INDEX IF NOT EXISTS idx_bill_number   ON hms_billing.bills(bill_number);
CREATE INDEX IF NOT EXISTS idx_pay_bill      ON hms_billing.payments(bill_id);
CREATE INDEX IF NOT EXISTS idx_pay_uuid      ON hms_billing.payments(uuid);

-- ============================================================
--  hms_notification schema — Notification Service tables
-- ============================================================

CREATE TABLE IF NOT EXISTS hms_notification.notifications (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    recipient_id    BIGINT          NOT NULL,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(20),
    type            VARCHAR(50)     NOT NULL,
    channel         VARCHAR(20)     NOT NULL DEFAULT 'EMAIL'
                                    CHECK (channel IN ('EMAIL','SMS','PUSH','IN_APP')),
    subject         VARCHAR(255),
    body            TEXT            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED')),
    retry_count     INT             NOT NULL DEFAULT 0,
    error_message   TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hms_notification.notification_preferences (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL UNIQUE,
    email_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    push_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    appt_reminder   BOOLEAN     NOT NULL DEFAULT TRUE,
    billing_alerts  BOOLEAN     NOT NULL DEFAULT TRUE,
    test_results    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notif_recipient ON hms_notification.notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notif_status    ON hms_notification.notifications(status);
CREATE INDEX IF NOT EXISTS idx_notif_type      ON hms_notification.notifications(type);
CREATE INDEX IF NOT EXISTS idx_notif_uuid      ON hms_notification.notifications(uuid);
