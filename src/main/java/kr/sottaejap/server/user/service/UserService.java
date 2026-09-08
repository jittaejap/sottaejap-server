package kr.sottaejap.server.user.service;

import kr.sottaejap.server.user.dto.UserMeResponse;
import kr.sottaejap.server.user.dto.UserSettingsRequest;

public interface UserService {

    UserMeResponse getMe(long userId);

    /** 예산 · 임계값 · D+N 설정 (API 3 · FR-01-03,04,06). 갱신 후 상태를 `GET /users/me`와 같은 모양으로 돌려준다. */
    UserMeResponse updateSettings(long userId, UserSettingsRequest request);
}
