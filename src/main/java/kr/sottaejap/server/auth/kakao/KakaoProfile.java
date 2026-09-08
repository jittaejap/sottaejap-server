package kr.sottaejap.server.auth.kakao;

/**
 * 카카오 /v2/user/me에서 서버가 쓰는 값만 추린 것 (E-56).
 *
 * @param providerUserId 카카오 회원번호 {@code id}. users.provider_user_id
 * @param nickname       kakao_account.profile.nickname, 없으면 properties.nickname (05 §2 실측 ①)
 * @param email          has_email이고 email_needs_agreement가 아닐 때만 값이 있다 (05 §2 실측 ②). 그 외 null
 */
public record KakaoProfile(String providerUserId, String nickname, String email) {
}
