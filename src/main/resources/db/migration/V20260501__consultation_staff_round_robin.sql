-- Thêm role tư vấn riêng và bảng trạng thái chia đều request tư vấn theo chi nhánh.

IF NOT EXISTS (SELECT 1 FROM Roles WHERE name = N'ConsultationStaff')
BEGIN
    INSERT INTO Roles (name, description, is_system_role)
    VALUES (N'ConsultationStaff', N'Nhân viên tư vấn khách hàng qua chat', 1);
END;

IF OBJECT_ID(N'dbo.ConsultationRoutingStates', N'U') IS NULL
BEGIN
    CREATE TABLE ConsultationRoutingStates (
        branch_id INT NOT NULL,
        last_assigned_user_id BIGINT NULL,
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_ConsultRouting_UpdatedAt DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ConsultationRoutingStates PRIMARY KEY CLUSTERED (branch_id),
        CONSTRAINT FK_ConsultRouting_Branch FOREIGN KEY (branch_id) REFERENCES Branches(id),
        CONSTRAINT FK_ConsultRouting_User FOREIGN KEY (last_assigned_user_id) REFERENCES Users(id)
    );
END;
