IF COL_LENGTH('InstallmentApplications', 'bank_code') IS NULL
BEGIN
    ALTER TABLE InstallmentApplications ADD bank_code NVARCHAR(50) NULL;
END
GO

IF COL_LENGTH('InstallmentApplications', 'prepayment_percent') IS NULL
BEGIN
    ALTER TABLE InstallmentApplications ADD prepayment_percent DECIMAL(5,2) NULL;
END
GO

IF COL_LENGTH('InstallmentApplications', 'request_pre_deposit') IS NULL
BEGIN
    ALTER TABLE InstallmentApplications ADD request_pre_deposit BIT NULL;
END
GO

IF COL_LENGTH('InstallmentApplications', 'pre_deposit_id') IS NULL
BEGIN
    ALTER TABLE InstallmentApplications ADD pre_deposit_id BIGINT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_installment_pre_deposit'
)
BEGIN
    ALTER TABLE InstallmentApplications
    ADD CONSTRAINT FK_installment_pre_deposit
    FOREIGN KEY (pre_deposit_id) REFERENCES Deposits(id);
END
GO

/*
  Backfill chuan hoa du lieu ho so tra gop cu bi ket o DRAFT:
  - Neu KH da dat coc cho xe (Pending/Confirmed/AwaitingPayment) => PENDING_DOCUMENT, request_pre_deposit = 0
  - Neu chua co coc => DEPOSIT_PENDING, request_pre_deposit = 1
  - Chi ap dung cho ho so DRAFT co dau hieu da submit (co chu ky + ngay ky + da dong y dieu khoan)
*/

;WITH LatestLockedDeposit AS (
    SELECT
        ia.id AS app_id,
        d.id AS deposit_id,
        ROW_NUMBER() OVER (
            PARTITION BY ia.id
            ORDER BY d.created_at DESC, d.id DESC
        ) AS rn
    FROM InstallmentApplications ia
    INNER JOIN Deposits d
        ON d.customer_id = ia.customer_id
       AND d.vehicle_id = ia.vehicle_id
       AND d.status IN (N'Pending', N'Confirmed', N'AwaitingPayment')
    WHERE ia.status = N'DRAFT'
      AND ia.signature_url IS NOT NULL
      AND LTRIM(RTRIM(ia.signature_url)) <> N''
      AND ia.signed_date IS NOT NULL
      AND ia.agreed_terms = 1
      AND ia.agreed_privacy = 1
),
Candidates AS (
    SELECT
        ia.id AS app_id,
        lld.deposit_id
    FROM InstallmentApplications ia
    LEFT JOIN LatestLockedDeposit lld
        ON lld.app_id = ia.id
       AND lld.rn = 1
    WHERE ia.status = N'DRAFT'
      AND ia.signature_url IS NOT NULL
      AND LTRIM(RTRIM(ia.signature_url)) <> N''
      AND ia.signed_date IS NOT NULL
      AND ia.agreed_terms = 1
      AND ia.agreed_privacy = 1
)
UPDATE ia
SET
    ia.deposit_id = COALESCE(c.deposit_id, ia.deposit_id),
    ia.request_pre_deposit = CASE WHEN c.deposit_id IS NULL THEN 1 ELSE 0 END,
    ia.status = CASE WHEN c.deposit_id IS NULL THEN N'DEPOSIT_PENDING' ELSE N'PENDING_DOCUMENT' END
FROM InstallmentApplications ia
INNER JOIN Candidates c ON c.app_id = ia.id;
GO
