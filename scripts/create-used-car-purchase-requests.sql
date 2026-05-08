IF OBJECT_ID(N'dbo.UsedCarPurchaseRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.UsedCarPurchaseRequests (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        branch_id INT NOT NULL,
        requested_by BIGINT NOT NULL,
        requested_by_name NVARCHAR(255) NULL,
        status NVARCHAR(40) NOT NULL,
        requested_purchase_price DECIMAL(18,0) NOT NULL,
        approved_purchase_price DECIMAL(18,0) NULL,
        manager_note NVARCHAR(MAX) NULL,
        admin_note NVARCHAR(MAX) NULL,
        vehicle_snapshot_json NVARCHAR(MAX) NOT NULL,
        image_snapshot_json NVARCHAR(MAX) NOT NULL,
        valuation_snapshot_json NVARCHAR(MAX) NOT NULL,
        created_vehicle_id BIGINT NULL,
        approved_by BIGINT NULL,
        approved_by_name NVARCHAR(255) NULL,
        approved_at DATETIME2 NULL,
        paid_by BIGINT NULL,
        paid_by_name NVARCHAR(255) NULL,
        paid_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL
    );

    CREATE INDEX IX_UsedCarPurchaseRequests_Branch_Status_CreatedAt
        ON dbo.UsedCarPurchaseRequests(branch_id, status, created_at DESC);

    CREATE INDEX IX_UsedCarPurchaseRequests_Status_CreatedAt
        ON dbo.UsedCarPurchaseRequests(status, created_at DESC);
END
