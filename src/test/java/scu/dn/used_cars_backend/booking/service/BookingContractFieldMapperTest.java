package scu.dn.used_cars_backend.booking.service;

import org.junit.jupiter.api.Test;
import scu.dn.used_cars_backend.booking.entity.BookingContract;
import scu.dn.used_cars_backend.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class BookingContractFieldMapperTest {

	@Test
	void resolveCustomerSignatureName_prefersTypedSignature() {
		BookingContract contract = new BookingContract();
		contract.setSignatureType("type");
		contract.setSignatureUrl("Kiki Dang");

		User customer = new User();
		customer.setName("Other Name");

		assertThat(BookingContractFieldMapper.resolveCustomerSignatureName(contract, customer))
				.isEqualTo("Kiki Dang");
	}

	@Test
	void resolveCustomerSignatureImageUrl_onlyForDrawType() {
		BookingContract contract = new BookingContract();
		contract.setSignatureType("draw");
		contract.setSignatureUrl("https://example.com/sig.png");
		assertThat(BookingContractFieldMapper.resolveCustomerSignatureImageUrl(contract))
				.isEqualTo("https://example.com/sig.png");

		contract.setSignatureType("type");
		assertThat(BookingContractFieldMapper.resolveCustomerSignatureImageUrl(contract)).isNull();
	}
}
