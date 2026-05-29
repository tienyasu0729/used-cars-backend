package scu.dn.used_cars_backend.booking.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.BookingContract;
import scu.dn.used_cars_backend.config.InstallmentContractProperties;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingContractDocxServiceTest {

	@Test
	void generateContractDocx_expandsMultiLineTermsBody() throws Exception {
		InstallmentContractProperties props = new InstallmentContractProperties();
		props.getSeller().setCompanyName("CÔNG TY ABC");
		BookingContractDocxService service = new BookingContractDocxService(new BookingContractFieldMapper(props));

		Booking booking = new Booking();
		booking.setId(10L);
		booking.setBookingDate(LocalDate.of(2026, 5, 29));
		booking.setTimeSlot(LocalTime.of(9, 0));

		Branch branch = new Branch();
		branch.setName("Chi nhánh Đà Nẵng");
		booking.setBranch(branch);

		Vehicle vehicle = new Vehicle();
		vehicle.setTitle("Toyota Vios 2020");
		booking.setVehicle(vehicle);

		BookingContract contract = new BookingContract();
		contract.setTermsVersion("v1");
		contract.setSignedAt(Instant.parse("2026-05-29T02:00:00Z"));
		contract.setSignatureType("type");
		contract.setSignatureUrl("Kiki Dang");

		User customer = new User();
		customer.setName("Kiki Dang");
		customer.setPhone("0901234567");

		String termsBody = "Điều 1: Quy định chung\nĐiều 2: Trách nhiệm khách hàng\nĐiều 3: Trách nhiệm đại lý";

		byte[] docx = service.generateContractDocx(booking, contract, customer, termsBody);

		assertThat(docx.length).isGreaterThan(1_000);
		try (var in = new ByteArrayInputStream(docx);
			 XWPFDocument document = new XWPFDocument(in)) {
			String text = document.getParagraphs().stream()
					.map(p -> p.getText() != null ? p.getText() : "")
					.reduce("", String::concat);
			assertThat(text).contains("Điều 1: Quy định chung");
			assertThat(text).contains("Điều 2: Trách nhiệm khách hàng");
			assertThat(text).contains("Điều 3: Trách nhiệm đại lý");
			assertThat(text).doesNotContain("{{TERMS_BODY}}");
			assertThat(text).doesNotContain("Mã xác thực nội dung");
		}
	}
}
