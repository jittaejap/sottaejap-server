package kr.sottaejap.server.user.repository;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);

    /** 소셜 계정 식별자 (E-56). V1 uq_users_provider가 유일성을 보장한다. */
    Optional<User> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);
}
