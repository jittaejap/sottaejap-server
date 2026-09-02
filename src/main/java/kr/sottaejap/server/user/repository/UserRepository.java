package kr.sottaejap.server.user.repository;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);
}
