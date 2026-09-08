package kr.sottaejap.server.notification.service;

import kr.sottaejap.server.common.enums.NotificationType;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.notification.domain.Notification;
import kr.sottaejap.server.notification.domain.PushSubscription;
import kr.sottaejap.server.notification.dto.NotificationListResponse;
import kr.sottaejap.server.notification.dto.PushSubscriptionRequest;
import kr.sottaejap.server.notification.repository.NotificationRepository;
import kr.sottaejap.server.notification.repository.PushSubscriptionRepository;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.service.CandidateService;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 인앱 알림 (FR-10). 회고 요청 1건은 두 경로로 만들어진다.
 *
 * <ul>
 *   <li>30분마다 도는 스케줄 — 사용자가 앱을 열지 않아도 Web Push가 나가야 하므로 이쪽이 본류다.
 *       푸시는 사용자 1건 트랜잭션을 커밋한 뒤에만 보낸다.</li>
 *   <li>목록을 열 때 — 스케줄이 돌지 않았거나 그 사이 가입한 사용자를 위한 그물이다. 이미 앱을 보고
 *       있는 사용자이므로 푸시는 보내지 않는다.</li>
 * </ul>
 *
 * <p>어느 쪽이든 하루 1건 가드를 통과해야 만들어지므로 둘이 겹쳐도 두 번 생기지 않는다 (FR-03-03).
 * 현재시각을 보므로 규칙 엔진이 아니라 service 계층에 있다 (NFR-01 · E-18).
 * 문구는 Spring 템플릿이다 — AI를 부르지 않으므로 ai 컨테이너가 내려가도 알림은 그대로 뜬다 (E-38).
 *
 * <p>어느 거래로 말을 걸지는 여기서 정하지 않는다. 규칙 엔진 ⓪({@link CandidateService})이 고른 후보를
 * 그대로 쓴다 — 알림 목록과 {@code GET /retrospects/candidates}가 다른 거래를 가리키면 안 된다 (E-62).
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA);

    /** 알림을 보내도 되는 창 07:00~21:00 (E-71). 계산 결과가 이 밖으로 나가면 경계로 끌어당긴다. */
    private static final int WINDOW_START_MINUTES = 7 * 60;
    private static final int WINDOW_END_MINUTES = 21 * 60;

    /** 어제 결제한 시각보다 이만큼 앞서 말을 건다 — "그 시간이 다가온다"를 회고할 여유를 준다 (E-71). */
    private static final int LEAD_MINUTES = 60;

    /** 스케줄이 30분마다 도므로 목표 시각도 같은 격자에 맞춘다. 아니면 영영 일치하지 않는다. */
    private static final int SLOT_MINUTES = 30;

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final CandidateService candidateService;
    private final UserRepository userRepository;
    private final WebPushSender webPushSender;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public NotificationListResponse findForUser(long userId) {
        // 그물 경로는 목표 시각이 지났을 때만 만든다. 오전에 목록을 연다고 저녁 알림을 미리 소진하면
        // 정작 그 시각에는 하루 1건 가드에 걸려 아무것도 오지 않는다.
        // 여기서는 푸시를 보내지 않는다 — 사용자가 이미 앱을 열고 목록을 보고 있고, 응답이 발송
        // (구독당 최대 10초)을 기다릴 이유가 없다.
        userRepository.findById(userId).ifPresent(user -> createTodayRetrospectDue(user, false));
        List<NotificationListResponse.Item> items = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationListResponse.Item::from)
                .toList();
        return new NotificationListResponse(notificationRepository.countByUserIdAndIsReadFalse(userId), items);
    }

    @Override
    @Transactional
    public void markAsRead(long userId, long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                // 남의 알림인지 없는 알림인지 구별해 주지 않는다.
                .filter(found -> found.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        notification.markAsRead();
    }

    @Override
    @Transactional
    public void subscribeToPush(long userId, PushSubscriptionRequest request) {
        // 같은 브라우저가 키를 새로 만들어 다시 구독하는 일이 흔하다. endpoint가 같으면 행을 늘리지 않는다.
        // endpoint는 V5에서 UNIQUE라 다른 사용자로 새 행을 만들 수 없다 — 같은 브라우저에 다른 계정이
        // 로그인한 경우이므로 소유자를 옮긴다. 남의 endpoint를 아는 사람은 그 기기로 자기 알림을
        // 보내게 만들 수 있지만, endpoint 자체가 브라우저와 서버만 아는 값이라 여기서 더 막지 않는다.
        pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> existing.replaceKeys(userId, request.keys().p256dh(), request.keys().auth()),
                        () -> pushSubscriptionRepository.save(PushSubscription.of(
                                userId,
                                request.endpoint(),
                                request.keys().p256dh(),
                                request.keys().auth(),
                                OffsetDateTime.now(clock))));
    }

    @Override
    @Transactional
    public void unsubscribeFromPush(long userId, String endpoint) {
        // 없는 구독을 지우려 해도 성공으로 둔다 — 해지는 여러 번 눌려도 결과가 같아야 한다.
        pushSubscriptionRepository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId() == userId)
                .ifPresent(pushSubscriptionRepository::delete);
    }

    /**
     * FR-10-01 — 회고 요청 알림을 각자의 시각에 만든다. 주기는 `NOTIFICATION_DAILY_CRON`으로 바꾼다.
     *
     * <p>사용자마다 목표 시각이 다르므로 스케줄은 30분마다 돌면서 "지금이 이 사람의 시각인가"만 본다.
     * 목표 시각은 30분 격자에 내린 값이므로(E-71) 주기도 30분 격자에 맞춰야 한다 — 시간마다로 바꾸면
     * `:30`이 목표인 사용자는 스케줄에 영영 걸리지 않고 그물 경로만 남는다.
     *
     * <p>일부러 @Transactional이 아니다. 스캔 전체를 한 트랜잭션으로 묶으면 사용자마다 잡는
     * `select … for update` 잠금이 스캔이 끝날 때까지 안 풀리고, 한 명에서 난 예외가 그날 전원의
     * 알림을 롤백한다. 트랜잭션은 {@link #createAndNotify}가 사용자 1건 단위로 연다.
     *
     * <p>ponytail: 사용자를 전부 훑는다. 인스턴스가 하나이고 데모 규모라 그래도 된다 (07 §1).
     * 오늘 알림이 이미 있으면 exists 질의 한 번에서 끝나므로, 후보 계산까지 가는 것은 하루에 사용자당
     * 한 번뿐이다. 사용자가 늘거나 인스턴스를 여러 대로 띄우면 페이징과 잠금이 필요하다.
     */
    @Scheduled(cron = "${notification.daily-cron:0 0,30 * * * *}", zone = "Asia/Seoul")
    public void createDailyRetrospectDue() {
        int created = 0;
        for (User user : userRepository.findAll()) {
            if (createAndNotify(user)) {
                created++;
            }
        }
        if (created > 0) {
            log.info("회고 요청 알림 {}건을 만들었습니다.", created);
        }
    }

    /**
     * 사용자 한 명 몫 — 저장은 트랜잭션 하나, 푸시는 그 커밋 뒤다.
     *
     * <p>커밋 전에 보내면 뒤에서 롤백됐을 때 알림 없는 푸시가 남는다. 사용자는 푸시를 눌러
     * `/notifications?ref=…`로 들어와 빈 목록을 본다.
     *
     * <p>한 사람의 데이터 문제로 그날 전원의 알림이 멈추면 안 되므로 예외는 여기서 잡고 다음 사용자로
     * 넘어간다.
     */
    private boolean createAndNotify(User user) {
        try {
            Optional<Notification> created = transactionTemplate.execute(
                    status -> createTodayRetrospectDue(user, true));
            if (created == null || created.isEmpty()) {
                return false;
            }
            webPushSender.send(user.getId(), created.get().getMessage(), created.get().getRefId());
            return true;
        } catch (RuntimeException failed) {
            log.warn("회고 요청 알림을 만들지 못했습니다 — userId={}", user.getId(), failed);
            return false;
        }
    }

    /**
     * FR-03-03 — 알림 경로는 하루 1건이다. 온보딩 연속 회고만 예외이고 그 경로는 여기가 아니다.
     *
     * @param exactSlot 스케줄은 목표 시각 슬롯에 정확히 맞을 때만(true), 그물은 목표 시각이 지났으면 언제든(false)
     */
    private Optional<Notification> createTodayRetrospectDue(User user, boolean exactSlot) {
        long userId = user.getId();
        if (hasTodayRetrospectDue(userId)) {
            return Optional.empty();
        }
        return pickCandidate(userId)
                .filter(candidate -> isDue(notifyAt(candidate.occurredAt()), exactSlot))
                .flatMap(candidate -> createExclusively(userId, candidate));
    }

    /**
     * 하루 1건 가드를 경쟁 없이 통과시킨다 (FR-03-03).
     *
     * <p>스케줄과 목록 조회가 동시에 들어오면 둘 다 위의 `exists`를 false로 읽고 각각 저장할 수 있다.
     * 사용자 행을 잠근 뒤 다시 확인해서, 잠금을 기다리는 사이 상대가 만들었으면 물러난다.
     * 잠금은 트랜잭션이 끝나면 풀린다 — 그래서 이 트랜잭션 안에서는 HTTP를 치지 않는다.
     */
    private Optional<Notification> createExclusively(long userId, CandidateView candidate) {
        userRepository.findByIdForUpdate(userId);
        if (hasTodayRetrospectDue(userId)) {
            return Optional.empty();
        }
        Notification notification = Notification.of(
                userId,
                NotificationType.RETROSPECT_DUE,
                candidate.transactionId(),
                message(candidate),
                OffsetDateTime.now(clock));
        notificationRepository.save(notification);
        return Optional.of(notification);
    }

    private boolean hasTodayRetrospectDue(long userId) {
        OffsetDateTime startOfToday = LocalDate.now(clock).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
        return notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                userId, NotificationType.RETROSPECT_DUE, startOfToday);
    }

    /**
     * 규칙 엔진 ⓪가 고른 후보 중 아직 말을 걸지 않은 첫 건 (E-62).
     *
     * <p>같은 거래로 두 번 알리지 않는 것은 여기서 막는다 — 후보 자격은 회고를 마쳐야 사라지는데,
     * 사용자가 알림을 무시하면 그 거래가 매일 다시 뽑히기 때문이다 (E-49 — 제외 상태는 저장하지 않는다).
     *
     * <p>후보를 한 페이지 가득 받는 이유가 이것이다. 적게 받으면 이미 알린 거래가 그만큼 쌓였을 때
     * 받은 것이 전부 걸러져 그다음 후보에 영영 닿지 못하고, 알림이 조용히 멈춘다.
     *
     * <p>ponytail: `findCandidates`는 최신 {@code MAX_LIMIT}건의 거래만 훑으므로 그 창을 넘어가지는
     * 못한다 (E-48 — 더 오래된 거래는 `from`·`to`로 본다). 창 안이 전부 알린 거래면 알릴 것이 없는 게 맞다.
     */
    private Optional<CandidateView> pickCandidate(long userId) {
        Set<Long> alreadyNotified = new HashSet<>(
                notificationRepository.findRefIdsByUserIdAndType(userId, NotificationType.RETROSPECT_DUE));
        return candidateService.findCandidates(userId, CandidateService.MAX_LIMIT, null, null).stream()
                .filter(candidate -> !alreadyNotified.contains(candidate.transactionId()))
                .findFirst();
    }

    /** 스케줄은 슬롯이 맞아야, 그물은 시각이 지났으면 된다. 그물이 있어야 스케줄을 놓친 날도 1건이 남는다. */
    private boolean isDue(LocalTime target, boolean exactSlot) {
        LocalTime now = LocalTime.now(clock);
        return exactSlot ? target.equals(floorToSlot(now)) : !target.isAfter(now);
    }

    /**
     * 알림 시각 = 결제 시각 − 1시간, 07:00~21:00으로 자르고 30분 격자에 맞춘 값 (E-71).
     *
     * <p>날짜는 보지 않는다 — 며칠 전 거래가 후보로 잡혀도 "그 시간대"만 맞추면 된다.
     */
    static LocalTime notifyAt(OffsetDateTime occurredAt) {
        LocalTime paidAt = occurredAt.atZoneSameInstant(TimeSlot.ZONE).toLocalTime();
        // 하루를 넘겨 되감으면 안 된다. 00:30 결제의 한 시간 전은 전날 23:30이지 오늘 23:30이 아니다.
        // 음수 그대로 두면 아래 clamp가 07:00으로 끌어올린다.
        int minutes = paidAt.getHour() * 60 + paidAt.getMinute() - LEAD_MINUTES;
        int slot = Math.floorDiv(minutes, SLOT_MINUTES) * SLOT_MINUTES;
        return minutesToTime(Math.clamp(slot, WINDOW_START_MINUTES, WINDOW_END_MINUTES));
    }

    private static LocalTime floorToSlot(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        return minutesToTime(minutes / SLOT_MINUTES * SLOT_MINUTES);
    }

    private static LocalTime minutesToTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    /** 알림은 결제 시각에 맞춰 가므로 "이맘때"가 사실이다. D+1이 아닌 후보만 날짜를 밝힌다. */
    private String message(CandidateView candidate) {
        LocalDate date = candidate.occurredAt().toLocalDate();
        String when = date.equals(LocalDate.now(clock).minusDays(1)) ? "어제" : MESSAGE_DATE.format(date);
        return "%s 이맘때 %s %,d원, 어땠는지 돌아볼까요?"
                .formatted(when, candidate.merchant(), candidate.amount());
    }
}
