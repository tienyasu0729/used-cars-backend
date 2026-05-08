package scu.dn.used_cars_backend;

import org.junit.jupiter.api.Test;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.config.CloudinaryProperties;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.MediaUploadContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryUploadServiceUrlTest {

	private final CloudinaryProperties props = new CloudinaryProperties("demo", "key", "secret");
	private final CloudinaryUploadService svc = new CloudinaryUploadService(props);

	@Test
	void acceptsValidAvatarUrl() {
		String url = "https://res.cloudinary.com/demo/image/upload/v1/used-cars/avatars/user_7.jpg";
		assertDoesNotThrow(() -> svc.assertSecureUrlMatchesSignedContext(url, MediaUploadContext.AVATAR, 7L));
	}

	@Test
	void rejectsWrongUserId() {
		String url = "https://res.cloudinary.com/demo/image/upload/v1/used-cars/avatars/user_99.jpg";
		assertThrows(BusinessException.class,
				() -> svc.assertSecureUrlMatchesSignedContext(url, MediaUploadContext.AVATAR, 7L));
	}

	@Test
	void rejectsWrongCloud() {
		String url = "https://res.cloudinary.com/other/image/upload/v1/used-cars/avatars/user_7.jpg";
		assertThrows(BusinessException.class,
				() -> svc.assertSecureUrlMatchesSignedContext(url, MediaUploadContext.AVATAR, 7L));
	}

	@Test
	void rejectsHttp() {
		String url = "http://res.cloudinary.com/demo/image/upload/v1/used-cars/avatars/user_7.jpg";
		assertThrows(BusinessException.class,
				() -> svc.assertSecureUrlMatchesSignedContext(url, MediaUploadContext.AVATAR, 7L));
	}
}
