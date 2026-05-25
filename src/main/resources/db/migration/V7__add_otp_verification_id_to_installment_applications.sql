IF COL_LENGTH('dbo.InstallmentApplications', 'otp_verification_id') IS NULL
BEGIN
    ALTER TABLE dbo.InstallmentApplications ADD otp_verification_id BIGINT NULL;
END
