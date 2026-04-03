package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.admin.AdminConfigEntryDto;
import scu.dn.used_cars_backend.dto.admin.AdminConfigUpsertItemDto;
import scu.dn.used_cars_backend.entity.SystemConfig;
import scu.dn.used_cars_backend.repository.SystemConfigRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminConfigService {

	private final SystemConfigRepository systemConfigRepository;

	@Transactional(readOnly = true)
	public List<AdminConfigEntryDto> listAll() {
		List<SystemConfig> rows = systemConfigRepository.findAll();
		rows.sort((a, b) -> a.getConfigKey().compareToIgnoreCase(b.getConfigKey()));
		List<AdminConfigEntryDto> out = new ArrayList<>(rows.size());
		for (SystemConfig c : rows) {
			out.add(AdminConfigEntryDto.builder()
					.configKey(c.getConfigKey())
					.value(c.getConfigValue() != null ? c.getConfigValue() : "")
					.description(c.getDescription())
					.build());
		}
		return out;
	}

	@Transactional
	public Map<String, Boolean> upsert(List<AdminConfigUpsertItemDto> items, long actorUserId) {
		if (items == null || items.isEmpty()) {
			return Map.of("success", true);
		}
		for (AdminConfigUpsertItemDto it : items) {
			if (it.getKey() == null || it.getKey().isBlank()) {
				continue;
			}
			String k = it.getKey().trim();
			String v = it.getValue() != null ? it.getValue() : "";
			SystemConfig row = systemConfigRepository.findByConfigKey(k).orElse(null);
			if (row == null) {
				row = new SystemConfig();
				row.setConfigKey(k);
			}
			row.setConfigValue(v);
			row.setUpdatedBy(actorUserId);
			systemConfigRepository.save(row);
		}
		return Map.of("success", true);
	}
}
