CREATE TABLE loan_config (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    term_months INT NOT NULL,
    interest_rate_percent DECIMAL(5,2) NOT NULL,
    min_down_payment_percent DECIMAL(5,2) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    description NVARCHAR(255) NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    created_by BIGINT NULL,
    CONSTRAINT UQ_loan_config_term_months UNIQUE (term_months)
);

INSERT INTO loan_config (term_months, interest_rate_percent, min_down_payment_percent, active, description, created_at, updated_at)
VALUES
    (12, 6.50, 30.00, 1, N'Kỳ hạn 12 tháng', GETUTCDATE(), GETUTCDATE()),
    (24, 7.00, 30.00, 1, N'Kỳ hạn 24 tháng', GETUTCDATE(), GETUTCDATE()),
    (36, 7.50, 30.00, 1, N'Kỳ hạn 36 tháng', GETUTCDATE(), GETUTCDATE()),
    (48, 7.80, 30.00, 1, N'Kỳ hạn 48 tháng', GETUTCDATE(), GETUTCDATE()),
    (60, 8.00, 30.00, 1, N'Kỳ hạn 60 tháng', GETUTCDATE(), GETUTCDATE()),
    (72, 8.50, 35.00, 1, N'Kỳ hạn 72 tháng', GETUTCDATE(), GETUTCDATE()),
    (84, 9.00, 40.00, 1, N'Kỳ hạn 84 tháng', GETUTCDATE(), GETUTCDATE());
