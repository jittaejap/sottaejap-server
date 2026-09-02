package kr.sottaejap.server.auth.controller;

import jakarta.validation.Valid;
import kr.sottaejap.server.auth.dto.LoginRequest;
import kr.sottaejap.server.auth.dto.LoginResponse;
import kr.sottaejap.server.auth.service.AuthService;
import kr.sottaejap.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
