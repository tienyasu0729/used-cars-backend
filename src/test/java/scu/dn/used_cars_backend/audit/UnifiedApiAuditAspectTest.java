package scu.dn.used_cars_backend.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnifiedApiAuditAspectTest {

	@Test
	void module_public_bookings() {
		assertEquals("bookings", UnifiedApiAuditAspect.moduleFromApiPath("/api/v1/bookings"));
	}

	@Test
	void module_admin_branches() {
		assertEquals("branches", UnifiedApiAuditAspect.moduleFromApiPath("/api/v1/admin/branches/3"));
	}

	@Test
	void module_admin_config() {
		assertEquals("config", UnifiedApiAuditAspect.moduleFromApiPath("/api/v1/admin/config"));
	}

	@Test
	void module_payment_nested() {
		assertEquals("payment", UnifiedApiAuditAspect.moduleFromApiPath("/api/v1/payment/vnpay/create"));
	}
}
