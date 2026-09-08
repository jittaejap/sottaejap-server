package kr.sottaejap.server.notification.repository;

import kr.sottaejap.server.notification.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);
}
