package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import scu.dn.used_cars_backend.entity.SystemAnnouncement;

public final class SystemAnnouncementDtos {

	private SystemAnnouncementDtos() {
	}

	public record AdminAnnouncementRowDto(Integer id, String title, String body, String notifKind, String audience,
			List<Long> targetUserIds, boolean published, boolean sendEmail, Instant emailSentAt, String emailLastError,
			Instant createdAt, Instant updatedAt) {

		public static AdminAnnouncementRowDto from(SystemAnnouncement a, List<Long> targets) {
			return new AdminAnnouncementRowDto(a.getId(), a.getTitle(), a.getBody(), a.getNotifKind(), a.getAudience(),
					targets, a.isPublished(), a.isSendEmail(), a.getEmailSentAt(), a.getEmailLastError(),
					a.getCreatedAt(), a.getUpdatedAt());
		}
	}

	public record CreateSystemAnnouncementRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 2000) String body,
			@NotBlank @Pattern(regexp = "announcement|email") String notifKind,
			@NotBlank @Pattern(regexp = "ALL_CUSTOMERS|STAFF_AND_MANAGERS|SPECIFIC_USERS") String audience,
			List<Long> targetUserIds,
			boolean sendEmail,
			boolean published) {
	}

	public record UpdateSystemAnnouncementRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 2000) String body,
			@NotBlank @Pattern(regexp = "announcement|email") String notifKind,
			@NotBlank @Pattern(regexp = "ALL_CUSTOMERS|STAFF_AND_MANAGERS|SPECIFIC_USERS") String audience,
			List<Long> targetUserIds,
			boolean sendEmail,
			boolean published) {
	}
}
