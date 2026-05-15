package scu.dn.used_cars_backend.sms.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.sms.dto.SmsConfirmRequest;
import scu.dn.used_cars_backend.sms.dto.SmsPendingResponse;
import scu.dn.used_cars_backend.sms.service.SmsService;

import java.util.List;

@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
public class SmsGatewayController {

    private final SmsService smsService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<SmsPendingResponse>>> getPendingMessages() {
        List<SmsPendingResponse> messages = smsService.getPendingMessages(10);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmMessage(@Valid @RequestBody SmsConfirmRequest request) {
        smsService.confirmMessage(request.getId(), request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
