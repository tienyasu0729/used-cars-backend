package scu.dn.used_cars_backend.dto.vehicle;

// DTO trả về gợi ý tìm kiếm cho ô search autocomplete
// type: loại gợi ý (brand / vehicle / year)
// text: nội dung hiển thị trên dropdown

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDto {

	private String type;
	private String text;

}
