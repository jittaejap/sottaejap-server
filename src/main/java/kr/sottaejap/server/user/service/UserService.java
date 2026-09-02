package kr.sottaejap.server.user.service;

import kr.sottaejap.server.user.dto.UserMeResponse;

public interface UserService {

    UserMeResponse getMe(long userId);
}
