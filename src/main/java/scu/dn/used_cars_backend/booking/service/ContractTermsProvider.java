package scu.dn.used_cars_backend.booking.service;

public final class ContractTermsProvider {

	public static final String CURRENT_VERSION = "1.0";

	private ContractTermsProvider() {}

	public static String getTermsContent() {
		return """
				HỢP ĐỒNG LÁI THỬ XE

				Điều 1 – Cam kết của Khách hàng
				1.1 Không gây hư hại, va chạm hoặc làm biến dạng xe trong suốt quá trình lái thử.
				1.2 Chịu hoàn toàn trách nhiệm bồi thường nếu xảy ra tai nạn, va chạm do lỗi của Khách hàng trong thời gian lái thử.
				1.3 Tuân thủ luật giao thông đường bộ Việt Nam và hướng dẫn của nhân viên đi cùng.

				Điều 2 – Giới hạn lái thử
				2.1 Phạm vi lái thử: trong khu vực do chi nhánh quy định; không tự ý rời khỏi lộ trình.
				2.2 Thời gian sử dụng: tối đa 30 phút kể từ khi nhận xe (hoặc theo thỏa thuận cụ thể).

				Điều 3 – Bồi thường
				3.1 Khách hàng chịu chi phí sửa chữa, thay thế phụ tùng nếu xe bị hư hỏng do lỗi của Khách hàng.
				3.2 Mức bồi thường được xác định trên cơ sở báo giá của hãng/đại lý ủy quyền.

				Điều 4 – Xác nhận giấy tờ
				4.1 Khách hàng xác nhận đã cung cấp bản chụp CCCD/CMND còn hiệu lực.
				4.2 Khách hàng xác nhận đã cung cấp bản chụp Giấy phép lái xe còn hiệu lực, phù hợp hạng xe lái thử.

				Điều 5 – Điều khoản chung
				5.1 Hợp đồng có hiệu lực kể từ thời điểm Khách hàng ký điện tử thành công.
				5.2 Mọi tranh chấp phát sinh được giải quyết trên tinh thần thương lượng; nếu không thống nhất, đưa ra tòa án có thẩm quyền.
				""";
	}
}
