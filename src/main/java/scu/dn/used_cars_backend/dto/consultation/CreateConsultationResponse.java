package scu.dn.used_cars_backend.dto.consultation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConsultationResponse {

	private long id;
	private boolean success = true;
}
