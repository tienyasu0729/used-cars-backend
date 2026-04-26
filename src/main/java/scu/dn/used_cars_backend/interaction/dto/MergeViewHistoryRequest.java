package scu.dn.used_cars_backend.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MergeViewHistoryRequest {

	@NotBlank
	private String guestId;
}
