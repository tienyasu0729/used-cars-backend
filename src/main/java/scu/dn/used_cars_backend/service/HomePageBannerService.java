package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.CreateHomeBannerRequest;
import scu.dn.used_cars_backend.dto.admin.HomeBannerAdminDto;
import scu.dn.used_cars_backend.dto.catalog.HomeBannerPublicDto;
import scu.dn.used_cars_backend.entity.HomePageBanner;
import scu.dn.used_cars_backend.repository.HomePageBannerRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomePageBannerService {

	private final HomePageBannerRepository homePageBannerRepository;
	private final CloudinaryUploadService cloudinaryUploadService;

	@Transactional(readOnly = true)
	public List<HomeBannerPublicDto> listPublic() {
		return toPublicDtos(homePageBannerRepository.findAllByOrderBySortOrderAscIdAsc());
	}

	@Transactional(readOnly = true)
	public List<HomeBannerAdminDto> listAllForAdmin() {
		return toAdminDtos(homePageBannerRepository.findAllByOrderBySortOrderAscIdAsc());
	}

	@Transactional
	public HomeBannerAdminDto create(CreateHomeBannerRequest req) {
		String url = req.getImageUrl().trim();
		cloudinaryUploadService.assertSecureUrlMatchesSignedContext(url, MediaUploadContext.HOME_BANNER, null);
		String pid = req.getCloudinaryPublicId() != null ? req.getCloudinaryPublicId().trim() : null;
		if (pid != null && pid.isEmpty()) {
			pid = null;
		}
		int next = homePageBannerRepository.findMaxSortOrder().orElse(0) + 1;
		HomePageBanner b = new HomePageBanner();
		b.setImageUrl(url);
		b.setCloudinaryPublicId(pid);
		b.setSortOrder(next);
		homePageBannerRepository.save(b);
		return toAdminDto(b);
	}

	@Transactional
	public void delete(long id) {
		HomePageBanner b = homePageBannerRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Không tìm thấy banner."));
		cloudinaryUploadService.destroyByPublicIdIfConfigured(b.getCloudinaryPublicId());
		homePageBannerRepository.delete(b);
	}

	private static List<HomeBannerPublicDto> toPublicDtos(List<HomePageBanner> list) {
		List<HomeBannerPublicDto> out = new ArrayList<>(list.size());
		for (HomePageBanner b : list) {
			out.add(HomeBannerPublicDto.builder()
					.id(b.getId())
					.imageUrl(b.getImageUrl())
					.sortOrder(b.getSortOrder())
					.build());
		}
		return out;
	}

	private static List<HomeBannerAdminDto> toAdminDtos(List<HomePageBanner> list) {
		List<HomeBannerAdminDto> out = new ArrayList<>(list.size());
		for (HomePageBanner b : list) {
			out.add(toAdminDto(b));
		}
		return out;
	}

	private static HomeBannerAdminDto toAdminDto(HomePageBanner b) {
		return HomeBannerAdminDto.builder()
				.id(b.getId())
				.imageUrl(b.getImageUrl())
				.cloudinaryPublicId(b.getCloudinaryPublicId())
				.sortOrder(b.getSortOrder())
				.createdAt(b.getCreatedAt())
				.build();
	}
}
