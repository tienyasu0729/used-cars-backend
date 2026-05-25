IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.Users') AND name = N'profile_completion_required'
)
BEGIN
    ALTER TABLE dbo.Users
        ADD profile_completion_required BIT NOT NULL
            CONSTRAINT DF_Users_profile_completion_required DEFAULT 0;
END
