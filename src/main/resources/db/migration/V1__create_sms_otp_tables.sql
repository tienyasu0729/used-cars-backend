CREATE TABLE device_keys (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    device_key    VARCHAR(64)   NOT NULL,
    device_name   VARCHAR(100)  NULL,
    is_active     BIT           NOT NULL DEFAULT 1,
    created_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    last_used_at  DATETIME2     NULL,
    CONSTRAINT UQ_device_keys_device_key UNIQUE (device_key)
);
GO

CREATE TABLE sms_messages (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    phone       VARCHAR(20)   NOT NULL,
    content     NVARCHAR(MAX) NOT NULL,
    status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    device_key  VARCHAR(64)   NULL,
    created_at  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    sent_at     DATETIME2     NULL,
    CONSTRAINT CK_sms_messages_status CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT FK_sms_messages_device_key FOREIGN KEY (device_key) REFERENCES device_keys(device_key)
);
GO

CREATE TABLE otp_verifications (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    phone           VARCHAR(20)   NOT NULL,
    otp_code        VARCHAR(6)    NOT NULL,
    reference_type  VARCHAR(50)   NOT NULL,
    reference_id    BIGINT        NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    max_attempts    INT           NOT NULL DEFAULT 5,
    expires_at      DATETIME2     NOT NULL,
    created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    verified_at     DATETIME2     NULL,
    CONSTRAINT CK_otp_verifications_status CHECK (status IN ('PENDING','VERIFIED','EXPIRED','EXHAUSTED'))
);
GO

CREATE INDEX idx_sms_messages_status ON sms_messages(status);
GO

CREATE INDEX idx_sms_messages_device_status ON sms_messages(device_key, status);
GO

CREATE INDEX idx_otp_phone_type_status ON otp_verifications(phone, reference_type, status);
GO
