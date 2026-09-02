package kr.sottaejap.server.auth.service;

import kr.sottaejap.server.auth.dto.LoginRequest;
import kr.sottaejap.server.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
