package kr.sottaejap.server.auth.controller;

import kr.sottaejap.server.auth.dto.LoginRequest;
import kr.sottaejap.server.auth.dto.LoginResponse;
import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.auth.service.AuthService;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/login HTTP 계약 (05 §2) — 본문 매핑, 봉투, 오류 코드 · 상태.
 * 서비스는 목이다. 분기 규칙은 AuthServiceImplTest, 카카오 통신은 KakaoOAuthClientTest가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final LoginResponse TOKEN = new LoginResponse("jwt-value", "Bearer",
            OffsetDateTime.of(2026, 9, 8, 18, 0, 0, 0, ZoneOffset.ofHours(9)));

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 데모_계정_로그인은_토큰_봉투를_돌려준다() throws Exception {
        when(authService.login(any())).thenReturn(TOKEN);

        mockMvc.perform(login("{\"provider\":\"LOCAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-08T18:00:00+09:00"))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(request.capture());
        assertEquals(AuthProvider.LOCAL, request.getValue().provider());
        assertNull(request.getValue().code());
        assertNull(request.getValue().redirectUri());
    }

    @Test
    void 카카오_본문의_code와_redirectUri가_서비스로_전달된다() throws Exception {
        when(authService.login(any())).thenReturn(TOKEN);

        mockMvc.perform(login("""
                {"provider":"KAKAO","code":"auth-code","redirectUri":"http://localhost:5173/auth/callback"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-value"));

        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(request.capture());
        assertEquals(AuthProvider.KAKAO, request.getValue().provider());
        assertEquals("auth-code", request.getValue().code());
        assertEquals("http://localhost:5173/auth/callback", request.getValue().redirectUri());
    }

    @Test
    void 카카오_코드_거부는_400_OAUTH_CODE_INVALID() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(AuthErrorCode.OAUTH_CODE_INVALID));

        mockMvc.perform(login("{\"provider\":\"KAKAO\",\"code\":\"used\",\"redirectUri\":\"http://localhost:5173/auth/callback\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OAUTH_CODE_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 카카오_장애는_502_OAUTH_PROVIDER_ERROR() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR));

        mockMvc.perform(login("{\"provider\":\"KAKAO\",\"code\":\"c\",\"redirectUri\":\"http://localhost:5173/auth/callback\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_ERROR"));
    }

    @Test
    void 데모_계정이_꺼져_있으면_403_DEMO_ACCOUNT_DISABLED() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(AuthErrorCode.DEMO_ACCOUNT_DISABLED));

        mockMvc.perform(login("{\"provider\":\"LOCAL\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCOUNT_DISABLED"));
    }

    @Test
    void provider가_없으면_400_INVALID_INPUT이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(login("{\"code\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(authService);
    }

    @Test
    void 모르는_provider_문자열은_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(login("{\"provider\":\"APPLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(authService);
    }

    @Test
    void 깨진_JSON은_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(login("{\"provider\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String body) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
