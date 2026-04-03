package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.customer.CustomerDepositRowDto;
import scu.dn.used_cars_backend.repository.DepositRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerDepositService {

	private final DepositRepository depositRepository;

	@Transactional(readOnly = true)
	public List<CustomerDepositRowDto> listForCustomer(long userId) {
		return depositRepository.findByCustomerIdOrderByCreatedAtDesc(userId).stream().map(CustomerDepositRowDto::from)
				.toList();
	}
}
