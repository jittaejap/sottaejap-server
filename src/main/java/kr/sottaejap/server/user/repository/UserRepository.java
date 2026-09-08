package kr.sottaejap.server.user.repository;

import kr.sottaejap.server.common.enums.AuthProvider;
import jakarta.persistence.LockModeType;
import kr.sottaejap.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);

    /** 소셜 계정 식별자 (E-56). V1 uq_users_provider가 유일성을 보장한다. */
    Optional<User> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);

    /**
     * 사용자 행을 잠근다 (`select ... for update`). 이 사용자에 대해 한 번에 하나의 트랜잭션만
     * 알림을 만들게 하려는 것이고, 잠금은 트랜잭션이 끝나면 풀린다.
     *
     * <p>알림 생성은 스케줄과 목록 조회 두 경로에 있어 check-then-insert 경쟁이 실재한다 (FR-03-03).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") long id);
}
