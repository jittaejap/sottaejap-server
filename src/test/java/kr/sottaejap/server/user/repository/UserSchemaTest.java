package kr.sottaejap.server.user.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V4(E-56) 스키마를 실제 PostgreSQL에 대고 확인한다 — email nullable · nickname · 소셜 식별자 유일.
 * 사용자 생성 API는 아직 없으므로(06 R15 2-3) 네이티브 INSERT로 행을 만들고 엔티티 매핑으로 읽는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class UserSchemaTest {

    private static final String INSERT_KAKAO_USER = """
            INSERT INTO users (email, auth_provider, provider_user_id, nickname, retrospect_delay_days, onboarding_completed)
            VALUES (:email, 'KAKAO', :providerUserId, :nickname, 1, FALSE)
            """;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 이메일_없는_카카오_사용자를_저장하고_읽을_수_있다() {
        insertKakaoUser(null, "kakao-1", "테스트닉");
        entityManager.clear();

        User user = userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1").orElseThrow();

        assertNull(user.getEmail());
        assertEquals("테스트닉", user.getNickname());
        assertEquals(AuthProvider.KAKAO, user.getAuthProvider());
    }

    @Test
    void 같은_카카오_계정은_두_번_만들_수_없다() {
        insertKakaoUser("a@example.com", "kakao-dup", "첫째");

        // 네이티브 쿼리는 Spring 예외 변환을 거치지 않는다 — Hibernate ConstraintViolationException(PersistenceException)
        assertThrows(PersistenceException.class,
                () -> insertKakaoUser(null, "kakao-dup", "둘째"));
    }

    @Test
    void 같은_이메일이라도_다른_소셜_계정이면_따로_만들어진다() {
        insertKakaoUser("demo@sottaejap.kr", "kakao-same-email", "데모와같은메일");
        entityManager.clear();

        Number count = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM users WHERE email = 'demo@sottaejap.kr'")
                .getSingleResult();

        assertEquals(2L, count.longValue());
    }

    @Test
    void 기준_금액_컬럼에_읽고_쓸_수_있다() {
        // V11 — 기본값은 null이고 백필하지 않는다 (E-115).
        insertKakaoUser(null, "kakao-base-amount", "기준금액");
        entityManager.clear();

        User user = userRepository
                .findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-base-amount").orElseThrow();
        assertNull(user.getOutlierBaseAmount());

        user.updateSettings(null, null, 150_000, null);
        entityManager.flush();
        entityManager.clear();

        assertEquals(150_000, userRepository
                .findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-base-amount").orElseThrow()
                .getOutlierBaseAmount());
    }

    @Test
    void 데모_계정은_표시_이름을_가진다() {
        User demo = userRepository.findByEmailAndAuthProvider("demo@sottaejap.kr", AuthProvider.LOCAL).orElseThrow();

        assertEquals("데모 사용자", demo.getNickname());
        assertTrue(demo.getProviderUserId() == null);
    }

    private void insertKakaoUser(String email, String providerUserId, String nickname) {
        entityManager.createNativeQuery(INSERT_KAKAO_USER)
                .setParameter("email", email)
                .setParameter("providerUserId", providerUserId)
                .setParameter("nickname", nickname)
                .executeUpdate();
        entityManager.flush();
    }
}
