package scu.dn.used_cars_backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatConversationRowDto {

	private long id;
	private String participantName;
	private String participantRole;
	private String lastMessage;
	private Instant lastMessageAt;
	private int unreadCount;
	/** Phiếu tư vấn đang chờ mà NV có thể tiếp nhận từ màn chat (cùng chi nhánh / Admin). */
	private Long consultationId;
	private String consultationStatus;
	private String vehicleInfo;
	private String vehiclePrice;
	/** true khi có phiếu pending gắn khách — hiển thị badge ưu tiên trên UI chat. */
	private Boolean consultationNewLead;
}
