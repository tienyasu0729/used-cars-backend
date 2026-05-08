package scu.dn.used_cars_backend.booking.service;

import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.booking.dto.ActiveContractTermsDto;

@Service
public class ContractTermsService {

	public ActiveContractTermsDto getActiveTerms() {
		return new ActiveContractTermsDto(ContractTermsProvider.CURRENT_VERSION, ContractTermsProvider.getTermsContent());
	}

	public String getTermsContentByVersionOrFallback(String version) {
		if (version != null && version.trim().equals(ContractTermsProvider.CURRENT_VERSION)) {
			return ContractTermsProvider.getTermsContent();
		}
		return ContractTermsProvider.getTermsContent();
	}
}
