package scu.dn.used_cars_backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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

import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Deposit;
import scu.dn.used_cars_backend.entity.SalesOrder;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendManagerBookingCreatedEmailAsync(
			String toEmail,
			String customerName,
			String vehicleTitle,
			LocalDate bookingDate,
			java.time.LocalTime timeSlot,
			String branchName,
			String bookingType,
			String note) {
		try {
			if (toEmail == null || toEmail.isBlank()) {
				return;
			}
			JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
			if (sender == null) {
				return;
			}
			String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
			if (from == null || from.isBlank()) {
				return;
			}
			String safeCustomer = customerName != null && !customerName.isBlank() ? customerName.trim() : "Quý khách";
			String safeVehicle = vehicleTitle != null && !vehicleTitle.isBlank() ? vehicleTitle.trim() : "Xe đã chọn";
			String safeBranch = branchName != null && !branchName.isBlank() ? branchName.trim() : "Chi nhánh showroom";
			String safeType = bookingType != null && !bookingType.isBlank() ? bookingType.trim() : "test_drive";
			String safeDate = bookingDate != null ? bookingDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
			String safeTime = timeSlot != null ? timeSlot.toString() : "";
			String safeNote = note != null && !note.isBlank() ? note.trim() : "Không có";

			String subject = "Xác nhận lịch hẹn lái thử/showroom";
			String body = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto\">"
					+ "<h2 style=\"color:#1A3C6E\">Lịch hẹn đã được tạo</h2>"
					+ "<p>Xin chào " + safeCustomer + ",</p>"
					+ "<p>Showroom đã tạo lịch hẹn cho bạn với thông tin sau:</p>"
					+ "<ul>"
					+ "<li><b>Xe:</b> " + safeVehicle + "</li>"
					+ "<li><b>Chi nhánh:</b> " + safeBranch + "</li>"
					+ "<li><b>Ngày hẹn:</b> " + safeDate + "</li>"
					+ "<li><b>Giờ hẹn:</b> " + safeTime + "</li>"
					+ "<li><b>Loại lịch:</b> " + safeType + "</li>"
					+ "<li><b>Ghi chú:</b> " + safeNote + "</li>"
					+ "</ul>"
					+ "<p>Nếu cần đổi lịch, vui lòng liên hệ showroom sớm để được hỗ trợ.</p>"
					+ "</div>";

			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(toEmail.trim());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
		}
		catch (Exception e) {
			log.warn("Gửi email xác nhận lịch hẹn thất bại cho {}: {}", toEmail, e.getMessage());
		}
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

	// ===== EMAIL XAC NHAN DON HANG DA TAO =====

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendOrderCreatedEmailAsync(SalesOrder order, Vehicle vehicle, User customer) {
		try {
			sendOrderCreatedEmail(order, vehicle, customer);
		} catch (Exception e) {
			log.warn("Loi khi gui email xac nhan don hang (orderId={}): {}", order.getId(), e.getMessage());
		}
	}

	private void sendOrderCreatedEmail(SalesOrder order, Vehicle vehicle, User customer) {
		if (customer.getEmail() == null || customer.getEmail().isBlank()) {
			return;
		}
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chua cau hinh, khong gui duoc email don hang cho khach {}.", customer.getId());
			return;
		}
		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			return;
		}

		String customerName = customer.getName() != null ? customer.getName() : "Quý khách";
		String vehicleName = vehicle.getTitle() != null && !vehicle.getTitle().isBlank()
				? vehicle.getTitle() : buildVehicleName(vehicle);
		String totalText = formatPrice(order.getTotalPrice());
		String depositText = order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0
				? formatPrice(order.getDepositAmount()) : "0 VNĐ";
		String remainingText = formatPrice(order.getRemainingAmount());

		String frontendBaseUrl = paymentGatewayConfigService.frontendBaseUrl();
		String orderLink = frontendBaseUrl + "/dashboard/orders";

		String subject = "Đơn hàng #" + order.getOrderNumber() + " đã được tạo — BanXeOTô Đà Nẵng";
		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
				+ "<h2 style=\"color: #1A3C6E;\">Đơn hàng đã được tạo!</h2>"
				+ "<p>Xin chào <b>" + customerName + "</b>,</p>"
				+ "<p>Đơn hàng của bạn tại BanXeOTô Đà Nẵng đã được tạo thành công:</p>"
				+ "<div style=\"background: #f9f9f9; padding: 16px; border-radius: 8px; margin: 16px 0;\">"
				+ "<p style=\"margin: 4px 0;\"><b>Mã đơn:</b> " + order.getOrderNumber() + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Xe:</b> " + vehicleName + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Tổng giá:</b> " + totalText + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Đã cọc:</b> " + depositText + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Còn lại:</b> " + remainingText + "</p>"
				+ "</div>"
				+ "<p>Nhân viên showroom sẽ hướng dẫn bạn hoàn tất thanh toán.</p>"
				+ "<p><a href=\"" + orderLink + "\" style=\"display: inline-block; padding: 12px 24px; "
				+ "background-color: #1A3C6E; color: #ffffff; text-decoration: none; border-radius: 6px;\">"
				+ "Xem đơn hàng</a></p>"
				+ "<p style=\"color: #888; font-size: 13px;\">BanXeOTô Đà Nẵng — Cảm ơn quý khách.</p>"
				+ "</div>";

		try {
			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(customer.getEmail());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
			log.info("Da gui email xac nhan don hang cho khach {} (orderId={})", customer.getId(), order.getId());
		} catch (Exception e) {
			log.warn("Gui email don hang that bai cho khach {}: {}", customer.getId(), e.getMessage());
		}
	}

	// ===== EMAIL LINK THANH TOAN ONLINE =====

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendOrderPaymentLinkEmailAsync(SalesOrder order, User customer, String paymentUrl, String gateway) {
		try {
			sendOrderPaymentLinkEmail(order, customer, paymentUrl, gateway);
		} catch (Exception e) {
			log.warn("Loi khi gui email link thanh toan (orderId={}): {}", order.getId(), e.getMessage());
		}
	}

	private void sendOrderPaymentLinkEmail(SalesOrder order, User customer, String paymentUrl, String gateway) {
		if (customer.getEmail() == null || customer.getEmail().isBlank()) {
			return;
		}
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chua cau hinh, khong gui duoc email link thanh toan cho khach {}.", customer.getId());
			return;
		}
		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			return;
		}

		String customerName = customer.getName() != null ? customer.getName() : "Quý khách";
		String remainingText = formatPrice(order.getRemainingAmount());

		String subject = "Thanh toán đơn hàng #" + order.getOrderNumber() + " — BanXeOTô Đà Nẵng";
		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
				+ "<h2 style=\"color: #1A3C6E;\">Thanh toán đơn hàng</h2>"
				+ "<p>Xin chào <b>" + customerName + "</b>,</p>"
				+ "<p>Nhân viên showroom đã tạo liên kết thanh toán cho đơn hàng <b>#"
				+ order.getOrderNumber() + "</b>:</p>"
				+ "<div style=\"background: #f9f9f9; padding: 16px; border-radius: 8px; margin: 16px 0;\">"
				+ "<p style=\"margin: 4px 0;\"><b>Số tiền:</b> " + remainingText + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Cổng thanh toán:</b> " + gateway + "</p>"
				+ "</div>"
				+ "<p>Nhấn nút bên dưới để thanh toán:</p>"
				+ "<p><a href=\"" + paymentUrl + "\" style=\"display: inline-block; padding: 14px 28px; "
				+ "background-color: #E8612A; color: #ffffff; text-decoration: none; border-radius: 6px; "
				+ "font-weight: bold;\">Thanh toán ngay</a></p>"
				+ "<p style=\"color: #888; font-size: 13px; margin-top: 16px;\">"
				+ "Hoặc copy link: <a href=\"" + paymentUrl + "\">" + paymentUrl + "</a></p>"
				+ "<p style=\"color: #888; font-size: 13px;\">BanXeOTô Đà Nẵng — Cảm ơn quý khách.</p>"
				+ "</div>";

		try {
			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(customer.getEmail());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
			log.info("Da gui email link thanh toan cho khach {} (orderId={}, gateway={})",
					customer.getId(), order.getId(), gateway);
		} catch (Exception e) {
			log.warn("Gui email link thanh toan that bai cho khach {}: {}", customer.getId(), e.getMessage());
		}
	}

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendTestDriveContractSignedEmailAsync(
			long bookingId,
			String toEmail,
			String customerName,
			String vehicleTitle,
			String bookingDate,
			String timeSlot,
			byte[] pdfBytes) {
		try {
			sendTestDriveContractSignedEmail(bookingId, toEmail, customerName, vehicleTitle, bookingDate, timeSlot, pdfBytes);
		} catch (Exception e) {
			log.warn("Loi khi gui email hop dong lai thu (bookingId={}): {}", bookingId, e.getMessage());
		}
	}

	private void sendTestDriveContractSignedEmail(
			long bookingId,
			String toEmail,
			String customerName,
			String vehicleTitle,
			String bookingDate,
			String timeSlot,
			byte[] pdfBytes) {
		if (toEmail == null || toEmail.isBlank()) {
			log.warn("Khach hop dong bookingId={} khong co email, bo qua goi thu.", bookingId);
			return;
		}
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chua cau hinh, khong gui duoc email hop dong cho bookingId={}.", bookingId);
			return;
		}
		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			return;
		}
		if (pdfBytes == null || pdfBytes.length == 0) {
			log.warn("PDF hop dong rong, khong dinh kem email bookingId={}.", bookingId);
			return;
		}
		String name = customerName != null && !customerName.isBlank() ? customerName : "Quý khách";
		String vTitle = vehicleTitle != null ? vehicleTitle : "xe";
		String dateLine = (bookingDate != null ? bookingDate : "—") + " — " + (timeSlot != null ? timeSlot : "—");
		String subject = "Hợp đồng lái thử đã ký (lịch #" + bookingId + ") — BanXeOTô Đà Nẵng";
		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
				+ "<h2 style=\"color: #1A3C6E;\">Cảm ơn bạn đã ký hợp đồng</h2>"
				+ "<p>Xin chào <b>" + esc(name) + "</b>,</p>"
				+ "<p>Chúng tôi gửi kèm bản PDF hợp đồng lái thử tương ứng với lịch hẹn của bạn.</p>"
				+ "<div style=\"background: #f9f9f9; padding: 16px; border-radius: 8px; margin: 16px 0;\">"
				+ "<p style=\"margin: 4px 0;\"><b>Xe:</b> " + esc(vTitle) + "</p>"
				+ "<p style=\"margin: 4px 0;\"><b>Thời gian hẹn:</b> " + esc(dateLine) + "</p>"
				+ "</div>"
				+ "<p style=\"color: #666; font-size: 14px;\">Lịch đã chuyển sang trạng thái chờ xác nhận. "
				+ "Bạn sẽ nhận cập nhật qua ứng dụng khi nhân viên liên hệ.</p>"
				+ "<p style=\"color: #888; font-size: 13px;\">File đính kèm: hop-dong-lai-thu-" + bookingId + ".pdf</p>"
				+ "</div>";
		try {
			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(toEmail);
			helper.setSubject(subject);
			helper.setText(body, true);
			String fileName = "hop-dong-lai-thu-" + bookingId + ".pdf";
			helper.addAttachment(fileName, new ByteArrayResource(pdfBytes), "application/pdf");
			sender.send(mm);
			log.info("Da gui email hop dong lai thu cho {} (bookingId={})", toEmail, bookingId);
		} catch (Exception e) {
			log.warn("Gửi email hợp đồng thất bại (bookingId={}): {}", bookingId, e.getMessage());
		}
	}

	// ===== EMAIL BIÊN LAI MUA XE THÀNH CÔNG =====

	/**
	 * Gửi email biên lai khi nhân viên xác nhận bán / bàn giao xe (đơn Completed).
	 * Caller truyền đủ dữ liệu đã load để tránh LazyInitializationException.
	 */
	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendOrderPurchaseCompletedEmailAsync(SalesOrder order, Vehicle vehicle,
			Branch branch, User customer, BigDecimal remainingBeforeCompleted) {
		try {
			sendOrderPurchaseCompletedEmail(order, vehicle, branch, customer, remainingBeforeCompleted);
		} catch (Exception e) {
			log.warn("Loi gui email bien lai mua xe (orderId={}): {}", order.getId(), e.getMessage());
		}
	}

	private void sendOrderPurchaseCompletedEmail(SalesOrder order, Vehicle vehicle,
			Branch branch, User customer, BigDecimal remainingBeforeCompleted) {
		if (customer.getEmail() == null || customer.getEmail().isBlank()) {
			return;
		}
		JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("SMTP chua cau hinh, khong gui duoc email bien lai cho khach {}.", customer.getId());
			return;
		}
		String from = (mailFromProp != null && !mailFromProp.isBlank()) ? mailFromProp : springMailUsername;
		if (from == null || from.isBlank()) {
			return;
		}

		String customerName = esc(customer.getName() != null ? customer.getName() : "Quý khách");
		String customerEmail = esc(customer.getEmail());
		String customerPhone = customer.getPhone() != null ? esc(customer.getPhone()) : "—";

		String vehicleName = vehicle.getTitle() != null && !vehicle.getTitle().isBlank()
				? esc(vehicle.getTitle()) : esc(buildVehicleName(vehicle));
		String vehicleYear = vehicle.getYear() != null ? String.valueOf(vehicle.getYear()) : "—";
		String vehicleListingId = vehicle.getListingId() != null ? esc(vehicle.getListingId()) : "";

		String branchName = branch.getName() != null ? esc(branch.getName()) : "—";
		String branchAddress = branch.getAddress() != null ? esc(branch.getAddress()) : "—";
		String branchPhone = branch.getPhone() != null ? esc(branch.getPhone()) : "—";

		String totalText = formatPrice(order.getTotalPrice());
		String depositText = order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0
				? formatPrice(order.getDepositAmount()) : "0 VNĐ";
		String remainingText = remainingBeforeCompleted != null ? formatPrice(remainingBeforeCompleted) : "0 VNĐ";
		String paymentMethod = order.getPaymentMethod() != null ? esc(order.getPaymentMethod()) : "—";
		String completedDate = formatInstant(order.getUpdatedAt());

		String frontendBaseUrl = paymentGatewayConfigService.frontendBaseUrl();
		String orderLink = frontendBaseUrl + "/dashboard/orders";

		String subject = "Biên lai giao dịch — Đơn #" + order.getOrderNumber() + " — BanXeOTô Đà Nẵng";

		String body = "<div style=\"font-family: Arial, sans-serif; max-width: 640px; margin: 0 auto; color: #333;\">"

				// Header
				+ "<div style=\"background: #1A3C6E; padding: 24px; border-radius: 8px 8px 0 0; text-align: center;\">"
				+ "<h1 style=\"color: #ffffff; margin: 0; font-size: 22px;\">BIÊN LAI GIAO DỊCH</h1>"
				+ "<p style=\"color: #ffffffcc; margin: 6px 0 0; font-size: 14px;\">BanXeOTô Đà Nẵng</p>"
				+ "</div>"

				// Mã đơn + ngày
				+ "<div style=\"background: #f0f4f8; padding: 16px 24px; border-bottom: 1px solid #e2e8f0;\">"
				+ "<table style=\"width: 100%; font-size: 14px;\">"
				+ "<tr><td><b>Mã đơn hàng:</b></td><td style=\"text-align: right;\">" + esc(order.getOrderNumber()) + "</td></tr>"
				+ "<tr><td><b>Ngày hoàn tất:</b></td><td style=\"text-align: right;\">" + completedDate + "</td></tr>"
				+ "<tr><td><b>Phương thức TT:</b></td><td style=\"text-align: right;\">" + paymentMethod + "</td></tr>"
				+ "</table>"
				+ "</div>"

				+ "<div style=\"padding: 24px;\">"

				// Thông tin showroom
				+ "<h3 style=\"color: #1A3C6E; margin: 0 0 8px; font-size: 15px; border-bottom: 2px solid #1A3C6E; padding-bottom: 4px;\">ĐƠN VỊ BÁN</h3>"
				+ "<table style=\"width: 100%; font-size: 14px; margin-bottom: 20px;\">"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Chi nhánh:</b></td><td>" + branchName + "</td></tr>"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Địa chỉ:</b></td><td>" + branchAddress + "</td></tr>"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Điện thoại:</b></td><td>" + branchPhone + "</td></tr>"
				+ "</table>"

				// Thông tin khách hàng
				+ "<h3 style=\"color: #1A3C6E; margin: 0 0 8px; font-size: 15px; border-bottom: 2px solid #1A3C6E; padding-bottom: 4px;\">KHÁCH HÀNG</h3>"
				+ "<table style=\"width: 100%; font-size: 14px; margin-bottom: 20px;\">"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Họ tên:</b></td><td>" + customerName + "</td></tr>"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Email:</b></td><td>" + customerEmail + "</td></tr>"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Điện thoại:</b></td><td>" + customerPhone + "</td></tr>"
				+ "</table>"

				// Thông tin xe
				+ "<h3 style=\"color: #1A3C6E; margin: 0 0 8px; font-size: 15px; border-bottom: 2px solid #1A3C6E; padding-bottom: 4px;\">THÔNG TIN XE</h3>"
				+ "<table style=\"width: 100%; font-size: 14px; margin-bottom: 20px;\">"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Tên xe:</b></td><td>" + vehicleName + "</td></tr>"
				+ "<tr><td style=\"padding: 3px 0;\"><b>Năm SX:</b></td><td>" + vehicleYear + "</td></tr>"
				+ (vehicleListingId.isEmpty() ? "" : "<tr><td style=\"padding: 3px 0;\"><b>Mã tin:</b></td><td>" + vehicleListingId + "</td></tr>")
				+ "</table>"

				// Bảng thanh toán
				+ "<h3 style=\"color: #1A3C6E; margin: 0 0 8px; font-size: 15px; border-bottom: 2px solid #1A3C6E; padding-bottom: 4px;\">CHI TIẾT THANH TOÁN</h3>"
				+ "<table style=\"width: 100%; font-size: 14px; border-collapse: collapse; margin-bottom: 8px;\">"
				+ "<tr><td style=\"padding: 6px 0; border-bottom: 1px solid #eee;\">Giá trị đơn hàng</td>"
				+ "<td style=\"padding: 6px 0; border-bottom: 1px solid #eee; text-align: right;\">" + totalText + "</td></tr>"
				+ "<tr><td style=\"padding: 6px 0; border-bottom: 1px solid #eee;\">Tiền đặt cọc</td>"
				+ "<td style=\"padding: 6px 0; border-bottom: 1px solid #eee; text-align: right;\">- " + depositText + "</td></tr>"
				+ "<tr><td style=\"padding: 6px 0; border-bottom: 1px solid #eee;\">Còn lại trước bàn giao</td>"
				+ "<td style=\"padding: 6px 0; border-bottom: 1px solid #eee; text-align: right;\">" + remainingText + "</td></tr>"
				+ "<tr><td style=\"padding: 8px 0; font-weight: bold; font-size: 15px;\">TỔNG THANH TOÁN</td>"
				+ "<td style=\"padding: 8px 0; text-align: right; font-weight: bold; font-size: 15px; color: #E8612A;\">"
				+ totalText + "</td></tr>"
				+ "</table>"

				+ "</div>"

				// CTA
				+ "<div style=\"padding: 0 24px 24px; text-align: center;\">"
				+ "<a href=\"" + orderLink + "\" style=\"display: inline-block; padding: 14px 28px; "
				+ "background-color: #1A3C6E; color: #ffffff; text-decoration: none; border-radius: 6px; "
				+ "font-weight: bold;\">Xem đơn hàng của tôi</a>"
				+ "</div>"

				// Footer
				+ "<div style=\"background: #f8fafc; padding: 16px 24px; border-radius: 0 0 8px 8px; "
				+ "border-top: 1px solid #e2e8f0; text-align: center; font-size: 12px; color: #94a3b8;\">"
				+ "<p style=\"margin: 0 0 4px;\">Đây là thông tin tham khảo giao dịch; không thay thế hóa đơn GTGT theo quy định thuế.</p>"
				+ "<p style=\"margin: 0;\">BanXeOTô Đà Nẵng — Cảm ơn quý khách đã tin tưởng.</p>"
				+ "</div>"

				+ "</div>";

		try {
			MimeMessage mm = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mm, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(customer.getEmail());
			helper.setSubject(subject);
			helper.setText(body, true);
			sender.send(mm);
			log.info("Da gui email bien lai mua xe cho khach {} (orderId={})", customer.getId(), order.getId());
		} catch (Exception e) {
			log.warn("Gui email bien lai that bai cho khach {}: {}", customer.getId(), e.getMessage());
		}
	}

	private String formatInstant(Instant instant) {
		if (instant == null) {
			return "—";
		}
		return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
				.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
				.format(instant);
	}

	private static String esc(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
