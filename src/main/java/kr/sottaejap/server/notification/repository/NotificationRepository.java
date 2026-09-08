package kr.sottaejap.server.notification.repository;

import kr.sottaejap.server.common.enums.NotificationType;
import kr.sottaejap.server.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(long userId);

    long countByUserIdAndIsReadFalse(long userId);

    /** FR-03-03 하루 1건. 오늘 0시(KST) 이후에 이미 만든 것이 있으면 더 만들지 않는다. */
    boolean existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(long userId, NotificationType type,
                                                             OffsetDateTime since);

    /** 같은 거래로 두 번 말을 걸지 않기 위해, 이미 알림을 만든 거래 id를 모아 온다. */
    @Query("select n.refId from Notification n where n.userId = :userId and n.type = :type and n.refId is not null")
    List<Long> findRefIdsByUserIdAndType(@Param("userId") long userId, @Param("type") NotificationType type);
}
