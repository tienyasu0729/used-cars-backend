package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ArticleCategories")
public class ArticleCategory extends BaseEntity {

	@Column(nullable = false, length = 100, unique = true)
	private String name;

	@Column(nullable = false, length = 120, unique = true)
	private String slug;

	@Column(length = 500)
	private String description;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	@Column(nullable = false)
	private boolean active = true;
}
