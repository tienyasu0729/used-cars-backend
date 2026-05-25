package scu.dn.used_cars_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.installment.contract")
public class InstallmentContractProperties {

	private Seller seller = new Seller();
	private int paymentDayOfMonth = 15;
	private String lateFeeRatePercent = "1,5%/tháng";

	public Seller getSeller() {
		return seller;
	}

	public void setSeller(Seller seller) {
		this.seller = seller != null ? seller : new Seller();
	}

	public int getPaymentDayOfMonth() {
		return paymentDayOfMonth;
	}

	public void setPaymentDayOfMonth(int paymentDayOfMonth) {
		this.paymentDayOfMonth = paymentDayOfMonth > 0 && paymentDayOfMonth <= 28 ? paymentDayOfMonth : 15;
	}

	public String getLateFeeRatePercent() {
		return lateFeeRatePercent;
	}

	public void setLateFeeRatePercent(String lateFeeRatePercent) {
		this.lateFeeRatePercent = lateFeeRatePercent == null || lateFeeRatePercent.isBlank()
				? "1,5%/tháng"
				: lateFeeRatePercent;
	}

	public static class Seller {
		private String companyName = "CÔNG TY ABC";
		private String taxCode = "";
		private String address = "";
		private String representative = "";

		public String getCompanyName() {
			return companyName;
		}

		public void setCompanyName(String companyName) {
			this.companyName = companyName == null ? "" : companyName;
		}

		public String getTaxCode() {
			return taxCode;
		}

		public void setTaxCode(String taxCode) {
			this.taxCode = taxCode == null ? "" : taxCode;
		}

		public String getAddress() {
			return address;
		}

		public void setAddress(String address) {
			this.address = address == null ? "" : address;
		}

		public String getRepresentative() {
			return representative;
		}

		public void setRepresentative(String representative) {
			this.representative = representative == null ? "" : representative;
		}
	}
}
