package scu.dn.used_cars_backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import scu.dn.used_cars_backend.entity.SystemConfig;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.SystemConfigRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;

import scu.dn.used_cars_backend.entity.Deposit;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Service gửi email thông báo (xe mới, đặt cọc thành công...).
// Chỉ gửi email, không gửi thông báo in-app.
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

	private final SystemConfigRepository systemConfigRepository;
	private final UserRepository userRepository;
	private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
	private final PaymentGatewayConfigService paymentGatewayConfigService;

	@Value("${app.mail.from:}")
	private String mailFromProp;

	@Value("${spring.mail.username:}")
	private String springMailUsername;

	/**
	 * Method async — gọi từ VehicleService sau khi tạo xe thành công.
	 * Chạy trong transaction riêng để không ảnh hưởng luồng tạo xe.
	 */
	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendNewVehicleNotificationAsync(Vehicle vehicle) {
		try {
			sendNewVehicleNotification(vehicle);
		} catch (Exception e) {
			log.warn("Lỗi khi gửi email thông báo xe mới (vehicleId={}): {}", vehicle.getId(), e.getMessage());
		}
	}

	// Gửi email thông báo xe mới đến tất cả customer active
	private void sendNewVehicleNotification(Vehicle vehicle) {
		// B1: Kiểm tra config notify_new_vehicle
		boolean shouldNotify = systemConfigRepository.findByConfigKey("notify_new_vehicle")
				.map(SystemConfig::getConfigValue)
				.map("true"::equalsIgnoreCase)
				.orElse(false);

		if (!shouldNotify) {
			return;
		}

		// B2: Lấy danh sách customer active
		List<User> customers = userRepository.findActiveCustomersWithRoles();
		if (customers.isEmpty()) {
			return;
		}

		// B3: Kiểm tra SMTP đã cấu hình
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chưa cấu hình, không gửi được email thông báo xe mới.");
			return;
		}

		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			log.warn("MAIL_FROM / spring.mail.username trống, không gửi được email thông báo xe mới.");
			return;
		}

		// B4: Build nội dung email
		String vehicleName = buildVehicleName(vehicle);
		String priceText = formatPrice(vehicle.getPrice());
		String frontendBaseUrl = paymentGatewayConfigService.frontendBaseUrl();
		String vehicleLink = frontendBaseUrl + "/vehicles/" + vehicle.getId();

		String subject = "Xe mới: " + vehicleName + " — BanXeOTô Đà Nẵng";
		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
				+ "<h2 style=\"color: #E8612A;\">Xe mới vừa được thêm!</h2>"
				+ "<p>Xin chào,</p>"
				+ "<p>Chúng tôi vừa có thêm xe mới trong hệ thống:</p>"
				+ "<div style=\"background: #f9f9f9; padding: 16px; border-radius: 8px; margin: 16px 0;\">"
				+ "<p style=\"margin: 4px 0;\"><b>Tên xe:</b> " + vehicleName + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Giá niêm yết:</b> " + priceText + "</p>"
				+ "</div>"
				+ "<p><a href=\"" + vehicleLink + "\" style=\"display: inline-block; padding: 12px 24px; "
				+ "background-color: #E8612A; color: #ffffff; text-decoration: none; border-radius: 6px;\">"
				+ "Xem chi tiết</a></p>"
				+ "<p style=\"color: #888; font-size: 13px;\">Bạn nhận được email này vì đã đăng ký tài khoản "
				+ "tại BanXeOTô Đà Nẵng.</p>"
				+ "</div>";

		// B5: Gửi email cho từng customer (lỗi 1 người không ảnh hưởng người khác)
		for (User customer : customers) {
			if (customer.getEmail() == null || customer.getEmail().isBlank()) {
				continue;
			}
			try {
				MimeMessage mm = sender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
				helper.setFrom(from);
				helper.setTo(customer.getEmail());
				helper.setSubject(subject);
				helper.setText(body, true);
				sender.send(mm);
			} catch (Exception e) {
				log.warn("Gửi email thông báo xe mới thất bại cho customer {}: {}",
						customer.getId(), e.getMessage());
			}
		}
	}

	// Ghép tên xe: Hãng + Dòng + Năm sản xuất
	private String buildVehicleName(Vehicle vehicle) {
		StringBuilder sb = new StringBuilder();
		if (vehicle.getCategory() != null) {
			sb.append(vehicle.getCategory().getName());
		}
		if (vehicle.getSubcategory() != null) {
			sb.append(" ").append(vehicle.getSubcategory().getName());
		}
		if (vehicle.getYear() != null) {
			sb.append(" ").append(vehicle.getYear());
		}
		return sb.toString().trim();
	}

	// Format giá tiền VNĐ
	private String formatPrice(BigDecimal price) {
		if (price == null) {
			return "Liên hệ";
		}
		NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
		return formatter.format(price) + " VNĐ";
	}

	// ===== EMAIL ĐẶT CỌC THÀNH CÔNG =====

	/**
	 * Gửi email thông báo đặt cọc thành công cho khách hàng.
	 * Chạy async + transaction riêng để không ảnh hưởng luồng đặt cọc chính.
	 * Caller phải truyền đủ dữ liệu (đã load sẵn) để tránh LazyInitializationException.
	 */
	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendDepositSuccessEmailAsync(Deposit deposit, Vehicle vehicle, User customer) {
		try {
			sendDepositSuccessEmail(deposit, vehicle, customer);
		} catch (Exception e) {
			log.warn("Lỗi khi gửi email đặt cọc thành công (depositId={}): {}", deposit.getId(), e.getMessage());
		}
	}

	// Gửi email xác nhận đặt cọc thành công đến 1 khách hàng
	private void sendDepositSuccessEmail(Deposit deposit, Vehicle vehicle, User customer) {
		// B1: Kiểm tra email khách hàng
		if (customer.getEmail() == null || customer.getEmail().isBlank()) {
			log.warn("Khách hàng {} không có email, bỏ qua gửi thông báo đặt cọc.", customer.getId());
			return;
		}

		// B2: Kiểm tra SMTP đã cấu hình
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chưa cấu hình, không gửi được email đặt cọc cho khách {}.", customer.getId());
			return;
		}

		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			log.warn("MAIL_FROM / spring.mail.username trống, không gửi được email đặt cọc.");
			return;
		}

		// B3: Chuẩn bị thông tin hiển thị
		String customerName = customer.getName() != null ? customer.getName() : "Quý khách";
		String vehicleName = vehicle.getTitle() != null && !vehicle.getTitle().isBlank()
				? vehicle.getTitle()
				: buildVehicleName(vehicle);
		String amountText = formatPrice(deposit.getAmount());
		String depositDateText = formatDate(deposit.getDepositDate());
		String expiryDateText = formatDate(deposit.getExpiryDate());
		String txnRef = deposit.getGatewayTxnRef() != null ? deposit.getGatewayTxnRef().trim() : "";

		String frontendBaseUrl = paymentGatewayConfigService.frontendBaseUrl();
		String depositLink = frontendBaseUrl + "/dashboard/deposits";

		// B4: Build nội dung email HTML
		String subject = "Xác nhận đặt cọc thành công — BanXeOTô Đà Nẵng";
		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
				+ "<h2 style=\"color: #E8612A;\">Đặt cọc thành công!</h2>"
				+ "<p>Xin chào <b>" + customerName + "</b>,</p>"
				+ "<p>Bạn đã đặt cọc thành công cho xe tại BanXeOTô Đà Nẵng. "
				+ "Dưới đây là thông tin chi tiết:</p>"
				+ "<div style=\"background: #f9f9f9; padding: 16px; border-radius: 8px; margin: 16px 0;\">"
				+ "<p style=\"margin: 4px 0;\"><b>Xe:</b> " + vehicleName + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Số tiền cọc:</b> " + amountText + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Ngày đặt cọc:</b> " + depositDateText + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Ngày hết hạn:</b> " + expiryDateText + "</p>"
				+ (txnRef.isEmpty() ? "" : "<p style=\"margin: 4px 0;\"><b>Mã giao dịch:</b> " + txnRef + "</p>")
				+ "</div>"
				+ "<p>Nhân viên showroom sẽ liên hệ xác nhận trong thời gian sớm nhất.</p>"
				+ "<p><a href=\"" + depositLink + "\" style=\"display: inline-block; padding: 12px 24px; "
				+ "background-color: #E8612A; color: #ffffff; text-decoration: none; border-radius: 6px;\">"
				+ "Xem đặt cọc của tôi</a></p>"
				+ "<p style=\"color: #888; font-size: 13px;\">Bạn nhận được email này vì đã đặt cọc xe "
				+ "tại BanXeOTô Đà Nẵng.</p>"
				+ "</div>";

		// B5: Gửi email
		try {
			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(customer.getEmail());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
			log.info("Đã gửi email đặt cọc thành công cho khách {} (depositId={})", customer.getId(), deposit.getId());
		} catch (Exception e) {
			log.warn("Gửi email đặt cọc thất bại cho khách {}: {}", customer.getId(), e.getMessage());
		}
	}

	// Format ngày dd/MM/yyyy cho email
	private String formatDate(LocalDate date) {
		if (date == null) {
			return "—";
		}
		return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}
}
