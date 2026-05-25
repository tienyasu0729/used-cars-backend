package scu.dn.used_cars_backend.service.installment;

import org.springframework.stereotype.Component;
import scu.dn.used_cars_backend.config.InstallmentContractProperties;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.InstallmentApplication;
import scu.dn.used_cars_backend.entity.LoanConfig;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class InstallmentContractFieldMapper {

	private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final InstallmentContractProperties contractProperties;

	public InstallmentContractFieldMapper(InstallmentContractProperties contractProperties) {
		this.contractProperties = contractProperties;
	}

	public Map<String, String> buildFields(InstallmentApplication app, Optional<LoanConfig> loanConfig) {
		LocalDate contractDate = app.getSignedDate() != null ? app.getSignedDate() : LocalDate.now(VN_ZONE);
		Branch branch = app.getVehicle() != null ? app.getVehicle().getBranch() : null;
		Vehicle vehicle = app.getVehicle();

		BigDecimal monthlyPayment = calculateMonthlyPayment(app, loanConfig);
		InstallmentContractProperties.Seller seller = contractProperties.getSeller();

		String sellerAddress = seller.getAddress();
		if (sellerAddress.isBlank() && branch != null) {
			sellerAddress = safe(branch.getAddress());
		}
		String sellerRep = seller.getRepresentative();
		if (sellerRep.isBlank() && branch != null && branch.getManager() != null) {
			sellerRep = safe(branch.getManager().getName());
		}
		String location = branch != null ? safe(branch.getName()) : "";

		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("CONTRACT_NO", String.valueOf(app.getId()));
		fields.put("DAY", String.valueOf(contractDate.getDayOfMonth()));
		fields.put("MONTH", String.valueOf(contractDate.getMonthValue()));
		fields.put("YEAR", String.valueOf(contractDate.getYear()));
		fields.put("LOCATION", location);
		fields.put("SELLER_COMPANY", seller.getCompanyName());
		fields.put("SELLER_TAX_CODE", seller.getTaxCode());
		fields.put("SELLER_ADDRESS", sellerAddress);
		fields.put("SELLER_REPRESENTATIVE", sellerRep);
		fields.put("BUYER_NAME", safe(resolveBuyerDisplayName(app)));
		fields.put("BUYER_ID", safe(app.getIdentityNumber()));
		fields.put("BUYER_ID_ISSUED_DATE", formatDate(app.getIdentityIssuedDate()));
		fields.put("BUYER_ID_ISSUED_PLACE", safe(app.getIdentityIssuedPlace()));
		fields.put("BUYER_PERMANENT_ADDRESS", safe(app.getPermanentAddress()));
		fields.put("BUYER_CURRENT_ADDRESS", safe(app.getCurrentAddress()));
		fields.put("PRODUCT_NAME", vehicle != null ? safe(vehicle.getTitle()) : "");
		fields.put("PRODUCT_DETAIL", buildProductDetail(vehicle));
		fields.put("VEHICLE_PRICE", formatMoney(app.getVehiclePrice()));
		fields.put("DOWN_PAYMENT", formatDownPayment(app.getPrepaymentAmount()));
		fields.put("LOAN_AMOUNT", formatMoney(app.getLoanAmount()));
		fields.put("LOAN_TERM_MONTHS", app.getLoanTermMonths() != null ? String.valueOf(app.getLoanTermMonths()) : "");
		fields.put("MONTHLY_PAYMENT", formatMoney(monthlyPayment));
		fields.put("PAYMENT_DAY", String.valueOf(contractProperties.getPaymentDayOfMonth()));
		fields.put("LATE_FEE_RATE", contractProperties.getLateFeeRatePercent());
		return fields;
	}

	private static String buildProductDetail(Vehicle vehicle) {
		if (vehicle == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(safe(vehicle.getTitle()));
		if (vehicle.getYear() != null) {
			sb.append(", năm sản xuất ").append(vehicle.getYear());
		}
		if (vehicle.getMileage() != null) {
			sb.append(", số km ").append(String.format(Locale.forLanguageTag("vi-VN"), "%,d", vehicle.getMileage()));
		}
		if (vehicle.getFuel() != null && !vehicle.getFuel().isBlank()) {
			sb.append(", nhiên liệu ").append(vehicle.getFuel());
		}
		return sb.toString();
	}

	private BigDecimal calculateMonthlyPayment(InstallmentApplication app, Optional<LoanConfig> loanConfig) {
		if (app.getLoanAmount() == null || app.getLoanTermMonths() == null || app.getLoanTermMonths() == 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal annualRate = loanConfig
				.map(LoanConfig::getInterestRatePercent)
				.orElse(new BigDecimal("8.0"));
		BigDecimal principal = app.getLoanAmount();
		int months = app.getLoanTermMonths();
		BigDecimal monthlyRate = annualRate.divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
		BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
		double pow = Math.pow(onePlusR.doubleValue(), months);
		BigDecimal numerator = principal.multiply(monthlyRate).multiply(BigDecimal.valueOf(pow));
		BigDecimal denominator = BigDecimal.valueOf(pow - 1);
		if (denominator.compareTo(BigDecimal.ZERO) == 0) {
			return principal.divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP);
		}
		return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
	}

	static String resolveBuyerDisplayName(InstallmentApplication app) {
		if (app.getFullName() != null && !app.getFullName().isBlank()) {
			return app.getFullName().trim();
		}
		User customer = app.getCustomer();
		if (customer != null && customer.getName() != null && !customer.getName().isBlank()) {
			return customer.getName().trim();
		}
		return "";
	}

	private static String formatDownPayment(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
			return "—";
		}
		return formatMoney(amount);
	}

	private static String formatMoney(BigDecimal amount) {
		if (amount == null) {
			return "—";
		}
		NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
		nf.setMaximumFractionDigits(0);
		nf.setMinimumFractionDigits(0);
		return nf.format(amount) + " đ";
	}

	private static String formatDate(LocalDate date) {
		return date == null ? "—" : VN_DATE.format(date);
	}

	private static String safe(Object value) {
		if (value == null) {
			return "—";
		}
		String s = value.toString().trim();
		return s.isEmpty() ? "—" : s;
	}
}
