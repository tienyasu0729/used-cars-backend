package scu.dn.used_cars_backend.entity;

/**
 * Giá trị {@code status} bảng Vehicles — khớp CK_Vehicles_Status trong DDL.
 */
public enum VehicleStatus {

	AVAILABLE("Available"),
	RESERVED("Reserved"),
	SOLD("Sold"),
	HIDDEN("Hidden"),
	IN_TRANSFER("InTransfer");

	private final String dbValue;

	VehicleStatus(String dbValue) {
		this.dbValue = dbValue;
	}

	public String getDbValue() {
		return dbValue;
	}
}
