package scu.dn.used_cars_backend.interaction.dto;

/**
 * Kết quả lưu xe yêu thích — phân biệt 201 (mới) vs 200 (đã lưu sẵn, idempotent).
 */
public record SaveVehicleResult(MessageDataResponse data, boolean created) {
}
