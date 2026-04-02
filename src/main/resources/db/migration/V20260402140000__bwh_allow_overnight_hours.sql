-- Ca đêm: close_time có thể < open_time (ví dụ 22:00–06:00). Chỉ cấm trùng nhau.
ALTER TABLE BranchWorkingHours DROP CONSTRAINT CK_BWH_TimeOrder;
ALTER TABLE BranchWorkingHours ADD CONSTRAINT CK_BWH_TimeOrder CHECK (open_time <> close_time);
