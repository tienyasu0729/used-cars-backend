package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.branch.BranchPublicDto;
import scu.dn.used_cars_backend.service.BranchService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

	private final BranchService branchService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<BranchPublicDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(branchService.listPublic()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BranchPublicDto>> detail(@PathVariable int id) {
		return ResponseEntity.ok(ApiResponse.success(branchService.getPublicById(id)));
	}

}
