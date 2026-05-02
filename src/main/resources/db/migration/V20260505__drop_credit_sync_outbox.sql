IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CreditSyncOutbox_status_next_retry' AND object_id = OBJECT_ID('CreditSyncOutbox'))
BEGIN
    DROP INDEX IX_CreditSyncOutbox_status_next_retry ON CreditSyncOutbox;
END;
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_CreditSyncOutbox_idempotency_key' AND object_id = OBJECT_ID('CreditSyncOutbox'))
BEGIN
    DROP INDEX UX_CreditSyncOutbox_idempotency_key ON CreditSyncOutbox;
END;
GO

IF OBJECT_ID('CreditSyncOutbox', 'U') IS NOT NULL
BEGIN
    DROP TABLE CreditSyncOutbox;
END;
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_InstallmentApplications_status_bank_loan' AND object_id = OBJECT_ID('InstallmentApplications'))
BEGIN
    DROP INDEX IX_InstallmentApplications_status_bank_loan ON InstallmentApplications;
END;
GO
