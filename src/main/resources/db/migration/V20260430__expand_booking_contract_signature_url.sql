IF EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'BookingContracts'
      AND COLUMN_NAME = 'signature_url'
      AND DATA_TYPE = 'nvarchar'
      AND CHARACTER_MAXIMUM_LENGTH <> -1
)
BEGIN
    ALTER TABLE dbo.BookingContracts ALTER COLUMN signature_url NVARCHAR(MAX) NULL;
END;
