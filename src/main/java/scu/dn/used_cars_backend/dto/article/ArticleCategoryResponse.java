package scu.dn.used_cars_backend.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCategoryResponse {

	private Long id;
	private String name;
	private String slug;
	private String description;
	private int sortOrder;
	private boolean active;
}
