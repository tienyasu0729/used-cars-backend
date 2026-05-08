/*
  SQL Server - Kiem tra callback credit theo loanId
  Cach dung:
    1) Sua @LoanId ben duoi
    2) Chay toan bo script
*/

SET NOCOUNT ON;

DECLARE @LoanId NVARCHAR(100) = N'8268733e-02e9-4ed9-aa26-2ded542aeadb';
DECLARE @TopN INT = 50;

PRINT '=== INPUT ===';
SELECT @LoanId AS loan_id_input, GETDATE() AS checked_at_server_time;

PRINT '=== 1) Ho so tra gop theo bank_loan_id ===';
SELECT
    ia.id,
    ia.bank_loan_id,
    ia.[status] AS app_status,
    ia.rejection_reason,
    ia.bank_pdf_url,
    ia.request_pre_deposit,
    ia.pre_deposit_id,
    ia.customer_id,
    ia.vehicle_id,
    ia.created_at,
    ia.updated_at
FROM InstallmentApplications ia
WHERE ia.bank_loan_id = @LoanId;

PRINT '=== 2) Status history cua ho so (moi nhat truoc) ===';
SELECT TOP (@TopN)
    h.id,
    h.application_id,
    h.old_status,
    h.new_status,
    h.note,
    h.changed_by,
    h.changed_at
FROM InstallmentStatusHistory h
INNER JOIN InstallmentApplications ia ON ia.id = h.application_id
WHERE ia.bank_loan_id = @LoanId
ORDER BY h.changed_at DESC, h.id DESC;

PRINT '=== 3) Audit log webhook/installment (moi nhat truoc) ===';
SELECT TOP (@TopN)
    a.id,
    a.[timestamp],
    a.[module],
    a.[action],
    a.details,
    a.user_id,
    a.user_name
FROM AuditLogs a
WHERE
    (a.[module] = 'INSTALLMENT' OR a.[module] = 'installment')
    AND (
        a.[action] LIKE 'WEBHOOK_%'
        OR a.[action] LIKE '%INSTALLMENT%'
        OR a.details LIKE '%' + @LoanId + '%'
    )
ORDER BY a.[timestamp] DESC, a.id DESC;

PRINT '=== 4) Noti tao ra sau callback (tham khao) ===';
DECLARE @NotiTable SYSNAME = NULL;
IF OBJECT_ID(N'dbo.InAppNotifications', N'U') IS NOT NULL
    SET @NotiTable = N'dbo.InAppNotifications';
ELSE IF OBJECT_ID(N'dbo.InAppNotification', N'U') IS NOT NULL
    SET @NotiTable = N'dbo.InAppNotification';

IF @NotiTable IS NULL
BEGIN
    PRINT 'Khong tim thay bang notification (InAppNotifications/InAppNotification). Bo qua muc 4.';
END
ELSE
BEGIN
    DECLARE @Sql NVARCHAR(MAX) = N'
SELECT TOP (@TopN)
    n.id,
    n.user_id,
    n.[type],
    n.title,
    n.body,
    n.link,
    n.is_read,
    n.created_at
FROM ' + @NotiTable + N' n
WHERE
    n.[type] = ''INSTALLMENT''
    AND (
        n.title LIKE N''%phe duyet%''
        OR n.title LIKE N''%tu choi%''
        OR n.body LIKE ''%'' + @LoanId + ''%''
    )
ORDER BY n.created_at DESC, n.id DESC;';

    EXEC sp_executesql
        @Sql,
        N'@TopN INT, @LoanId NVARCHAR(100)',
        @TopN = @TopN,
        @LoanId = @LoanId;
END

PRINT '=== 5) Kiem tra nhanh ket qua mong doi ===';
;WITH x AS (
    SELECT TOP 1
        ia.id,
        ia.[status] AS current_status,
        ia.updated_at
    FROM InstallmentApplications ia
    WHERE ia.bank_loan_id = @LoanId
    ORDER BY ia.updated_at DESC, ia.id DESC
)
SELECT
    x.id AS application_id,
    x.current_status,
    CASE
        WHEN x.current_status = 'APPROVED' THEN 'OK_APPROVED'
        WHEN x.current_status IS NULL THEN 'NOT_FOUND'
        ELSE 'NOT_APPROVED_YET'
    END AS verdict,
    x.updated_at
FROM x;

PRINT '=== 6) Neu callback tra SUCCESS nhung chua APPROVED, check 3 diem sau ===';
SELECT
    'A) Callback payload status phai la APPROVED/REJECTED (khong nhan gia tri khac)' AS check_item
UNION ALL
SELECT
    'B) Callback co the bi race -> backend tra 202 WEBHOOK_ACCEPTED_RETRY, can retry'
UNION ALL
SELECT
    'C) bank_loan_id callback khong map duoc vao InstallmentApplications.bank_loan_id';
