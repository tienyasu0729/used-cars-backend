package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.article.ArticleCategoryResponse;
import scu.dn.used_cars_backend.dto.article.CreateCategoryRequest;
import scu.dn.used_cars_backend.entity.ArticleCategory;
import scu.dn.used_cars_backend.repository.ArticleCategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleCategoryService {

	private final ArticleCategoryRepository categoryRepository;

	@Transactional(readOnly = true)
	public List<ArticleCategoryResponse> listActiveCategories() {
		return categoryRepository.findByActiveTrueOrderBySortOrderAsc()
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<ArticleCategoryResponse> listAllCategories() {
		return categoryRepository.findAll()
				.stream().map(this::toResponse).toList();
	}

	@Transactional
	public ArticleCategoryResponse create(CreateCategoryRequest req) {
		String slug = req.getSlug() != null && !req.getSlug().isBlank()
				? req.getSlug().trim()
				: toSlug(req.getName());

		if (categoryRepository.existsBySlug(slug)) {
			throw new BusinessException(ErrorCode.CATEGORY_SLUG_CONFLICT, "Slug danh mục đã tồn tại.");
		}

		ArticleCategory cat = new ArticleCategory();
		cat.setName(req.getName().trim());
		cat.setSlug(slug);
		cat.setDescription(req.getDescription());
		cat.setSortOrder(req.getSortOrder());
		cat.setActive(req.isActive());
		categoryRepository.save(cat);
		return toResponse(cat);
	}

	@Transactional
	public ArticleCategoryResponse update(Long id, CreateCategoryRequest req) {
		ArticleCategory cat = categoryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_CATEGORY_NOT_FOUND, "Không tìm thấy danh mục."));

		String slug = req.getSlug() != null && !req.getSlug().isBlank()
				? req.getSlug().trim()
				: toSlug(req.getName());

		if (!slug.equals(cat.getSlug()) && categoryRepository.existsBySlug(slug)) {
			throw new BusinessException(ErrorCode.CATEGORY_SLUG_CONFLICT, "Slug danh mục đã tồn tại.");
		}

		cat.setName(req.getName().trim());
		cat.setSlug(slug);
		cat.setDescription(req.getDescription());
		cat.setSortOrder(req.getSortOrder());
		cat.setActive(req.isActive());
		categoryRepository.save(cat);
		return toResponse(cat);
	}

	@Transactional
	public void delete(Long id) {
		ArticleCategory cat = categoryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_CATEGORY_NOT_FOUND, "Không tìm thấy danh mục."));
		categoryRepository.delete(cat);
	}

	private ArticleCategoryResponse toResponse(ArticleCategory cat) {
		return ArticleCategoryResponse.builder()
				.id(cat.getId())
				.name(cat.getName())
				.slug(cat.getSlug())
				.description(cat.getDescription())
				.sortOrder(cat.getSortOrder())
				.active(cat.isActive())
				.build();
	}

	static String toSlug(String text) {
		if (text == null) return "";
		return text.trim().toLowerCase()
				.replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
				.replaceAll("[èéẹẻẽêềếệểễ]", "e")
				.replaceAll("[ìíịỉĩ]", "i")
				.replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
				.replaceAll("[ùúụủũưừứựửữ]", "u")
				.replaceAll("[ỳýỵỷỹ]", "y")
				.replaceAll("[đ]", "d")
				.replaceAll("[^a-z0-9\\s-]", "")
				.replaceAll("[\\s]+", "-")
				.replaceAll("-+", "-")
				.replaceAll("^-|-$", "");
	}
}
