package scu.dn.used_cars_backend.service.installment;

import org.junit.jupiter.api.Test;
import scu.dn.used_cars_backend.config.InstallmentContractProperties;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.LoanConfig;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentContractFieldMapperTest {

	@Test
	void buildFields_mapsCoreLoanAndBuyerFields() {
		InstallmentContractProperties props = new InstallmentContractProperties();
		props.getSeller().setCompanyName("CÔNG TY ABC");
		InstallmentContractFieldMapper mapper = new InstallmentContractFieldMapper(props);

		InstallmentApplication app = new InstallmentApplication();
		app.setId(42L);
		app.setFullName("Nguyen Van A");
		app.setIdentityNumber("001122334455");
		app.setPermanentAddress("Da Nang");
		app.setCurrentAddress("Da Nang");
		app.setVehiclePrice(new BigDecimal("139000000"));
		app.setPrepaymentAmount(BigDecimal.ZERO);
		app.setLoanAmount(new BigDecimal("139000000"));
		app.setLoanTermMonths(12);

		Vehicle vehicle = new Vehicle();
		vehicle.setTitle("Toyota Vios 2020");
		vehicle.setYear(2020);
		vehicle.setMileage(35000);
		app.setVehicle(vehicle);

		LoanConfig config = new LoanConfig();
		config.setTermMonths(12);
		config.setInterestRatePercent(new BigDecimal("8.0"));

		Map<String, String> fields = mapper.buildFields(app, Optional.of(config));

		assertThat(fields.get("CONTRACT_NO")).isEqualTo("42");
		assertThat(fields.get("SELLER_COMPANY")).isEqualTo("CÔNG TY ABC");
		assertThat(fields.get("BUYER_NAME")).isEqualTo("Nguyen Van A");
		assertThat(fields.get("PRODUCT_NAME")).contains("Toyota Vios");
		assertThat(fields.get("VEHICLE_PRICE")).contains("139");
		assertThat(fields.get("DOWN_PAYMENT")).isEqualTo("—");
		assertThat(fields.get("LOAN_TERM_MONTHS")).isEqualTo("12");
		assertThat(fields.get("MONTHLY_PAYMENT")).isNotBlank();
	}
}
