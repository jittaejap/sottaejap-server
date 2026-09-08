package kr.sottaejap.server.notification.service;

import kr.sottaejap.server.common.enums.NotificationType;
import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.notification.domain.Notification;
import kr.sottaejap.server.notification.repository.NotificationRepository;
import kr.sottaejap.server.notification.repository.PushSubscriptionRepository;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.service.CandidateService;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림 생성은 "언제 · 몇 건"만 정한다 (FR-10-01 · FR-03-03). 어느 거래로 말을 걸지는 규칙 엔진 ⓪이
 * 정하므로 여기서 검증하지 않는다 (E-62) — 대신 그 후보의 결제 시각에서 알림 시각이 어떻게 나오는지를 본다.
 */
class NotificationServiceImplTest {

    private static final long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    private NotificationRepository notificationRepository;
    private CandidateService candidateService;
    private UserRepository userRepository;
    private WebPushSender webPushSender;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        candidateService = mock(CandidateService.class);
        userRepository = mock(UserRepository.class);
        webPushSender = mock(WebPushSender.class);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(0L);
        when(notificationRepository.findRefIdsByUserIdAndType(USER_ID, NotificationType.RETROSPECT_DUE))
                .thenReturn(List.of());
    }

    /**
     * E-71 — 결제 시각 −1시간, 07:00~21:00으로 자르고 30분 격자에 맞춘다.
     * 자정을 넘겨 되감기면 00:30 결제가 21:00으로 튄다 — 마지막 두 줄이 그것을 막는다.
     */
    @ParameterizedTest(name = "{0} 결제 → {1} 알림")
    @CsvSource({
            "19:40, 18:30",
            "01:00, 07:00",
            "07:00, 07:00",
            "22:00, 21:00",
            "23:30, 21:00",
            "00:30, 07:00",
            "00:00, 07:00",
    })
    void computesNotifyTimeFromPaymentTime(String paidAt, String expected) {
        OffsetDateTime occurredAt = TODAY.minusDays(1)
                .atTime(LocalTime.parse(paidAt)).atZone(TimeSlot.ZONE).toOffsetDateTime();

        assertThat(NotificationServiceImpl.notifyAt(occurredAt)).isEqualTo(LocalTime.parse(expected));
    }

    @Test
    void scheduleCreatesNotificationOnlyInTheMatchingSlot() {
        givenNoNotificationToday();
        givenCandidatePaidAt(LocalTime.of(19, 40));
        givenSingleUser();

        serviceAt(LocalTime.of(9, 0)).createDailyRetrospectDue();
        verify(notificationRepository, never()).save(any());

        serviceAt(LocalTime.of(18, 30)).createDailyRetrospectDue();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.RETROSPECT_DUE);
        assertThat(saved.getValue().getRefId()).isEqualTo(1043L);
        // 문구는 Spring 템플릿이다 — AI가 없어도 알림이 뜬다 (E-38).
        assertThat(saved.getValue().getMessage()).isEqualTo("어제 이맘때 ○○배달 12,000원, 어땠는지 돌아볼까요?");
        verify(webPushSender).send(eq(USER_ID), any(), eq(1043L));
    }

    @Test
    void listDoesNotBurnTheNotificationBeforeItsTime() {
        givenNoNotificationToday();
        givenCandidatePaidAt(LocalTime.of(19, 40));
        givenUser();

        // 목표는 18:30이다. 오전에 목록을 연다고 미리 만들면 정작 저녁에는 하루 1건 가드에 걸린다.
        assertThat(serviceAt(LocalTime.of(10, 0)).findForUser(USER_ID).notifications()).isEmpty();
        verify(notificationRepository, never()).save(any());

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);
        verify(notificationRepository).save(any());
    }

    @Test
    void doesNotCreateSecondNotificationOnTheSameDay() {
        givenUser();
        when(notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                eq(USER_ID), eq(NotificationType.RETROSPECT_DUE), any())).thenReturn(true);

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);

        verify(notificationRepository, never()).save(any());
        // 만들지 않기로 정해졌으면 후보를 계산하지도 않는다 — 기준선 조회가 통째로 걸려 있다.
        verify(candidateService, never()).findCandidates(anyLong(), anyInt(), any(), any());
    }

    @Test
    void skipsTransactionsAlreadyNotified() {
        givenNoNotificationToday();
        givenUser();
        when(notificationRepository.findRefIdsByUserIdAndType(USER_ID, NotificationType.RETROSPECT_DUE))
                .thenReturn(List.of(1043L));
        when(candidateService.findCandidates(eq(USER_ID), anyInt(), eq(null), eq(null)))
                .thenReturn(List.of(
                        candidate(1043L, "○○배달", 12000, LocalTime.of(19, 40)),
                        candidate(1044L, "△△카페", 4500, LocalTime.of(19, 40))));

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getRefId()).isEqualTo(1044L);
    }

    /** 이미 알린 거래가 쌓여도 그다음 후보에 닿아야 한다 — 적게 받으면 전부 걸러져 알림이 멈춘다. */
    @Test
    void asksForAFullPageOfCandidates() {
        givenNoNotificationToday();
        givenCandidatePaidAt(LocalTime.of(19, 40));
        givenUser();

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);

        verify(candidateService).findCandidates(USER_ID, CandidateService.MAX_LIMIT, null, null);
    }

    /** 스케줄과 목록 조회가 겹치면 둘 다 가드를 통과한다. 잠근 뒤 다시 확인해서 뒤늦은 쪽이 물러난다. */
    @Test
    void doesNotCreateWhenAnotherTransactionWonTheLock() {
        givenCandidatePaidAt(LocalTime.of(19, 40));
        givenUser();
        when(notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                eq(USER_ID), eq(NotificationType.RETROSPECT_DUE), any()))
                // 첫 확인은 통과하지만, 잠금을 기다리는 사이 상대가 만들어 두 번째 확인에서 걸린다.
                .thenReturn(false, true);

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(notificationRepository, never()).save(any());
        verify(webPushSender, never()).send(anyLong(), any(), any());
    }

    /** 목록을 보고 있는 사용자에게 푸시를 보내며 응답을 늦출 이유가 없다. */
    @Test
    void listPathCreatesTheNotificationButSendsNoPush() {
        givenNoNotificationToday();
        givenCandidatePaidAt(LocalTime.of(19, 40));
        givenUser();

        serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID);

        verify(notificationRepository).save(any());
        verify(webPushSender, never()).send(anyLong(), any(), any());
    }

    /** 한 사람의 데이터 문제가 그날 전원의 알림을 막으면 안 된다. */
    @Test
    void scheduleKeepsGoingWhenOneUserFails() {
        long brokenUserId = 2L;
        givenNoNotificationToday();
        givenCandidatePaidAt(LocalTime.of(19, 40));
        // user()를 when(...) 안에서 만들면 스텁이 겹쳐 UnfinishedStubbingException이 난다.
        List<User> users = List.of(user(brokenUserId), user(USER_ID));
        when(userRepository.findAll()).thenReturn(users);
        when(notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                eq(brokenUserId), eq(NotificationType.RETROSPECT_DUE), any()))
                .thenThrow(new IllegalStateException("이 사용자에서 조회가 깨졌다"));

        serviceAt(LocalTime.of(18, 30)).createDailyRetrospectDue();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        verify(webPushSender).send(eq(USER_ID), any(), eq(1043L));
    }

    @Test
    void createsNothingWhenNoCandidateExists() {
        givenNoNotificationToday();
        givenUser();
        when(candidateService.findCandidates(eq(USER_ID), anyInt(), eq(null), eq(null))).thenReturn(List.of());

        assertThat(serviceAt(LocalTime.of(20, 0)).findForUser(USER_ID).notifications()).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadRejectsAnotherUsersNotification() {
        Notification othersNotification = Notification.of(
                99L, NotificationType.RETROSPECT_DUE, 1L, "남의 알림", OffsetDateTime.now());
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(othersNotification));

        assertThatThrownBy(() -> serviceAt(LocalTime.of(20, 0)).markAsRead(USER_ID, 7L))
                .isInstanceOf(BusinessException.class);
        assertThat(othersNotification.isRead()).isFalse();
    }

    private NotificationServiceImpl serviceAt(LocalTime now) {
        Clock clock = Clock.fixed(TODAY.atTime(now).atZone(TimeSlot.ZONE).toInstant(), TimeSlot.ZONE);
        // 트랜잭션 관리자는 목이다 — 여기서 볼 것은 "사용자 1건마다 실행하고 그 뒤에 보내는가"뿐이다.
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(mock(PlatformTransactionManager.class));
        return new NotificationServiceImpl(notificationRepository, mock(PushSubscriptionRepository.class),
                candidateService, userRepository, webPushSender, clock, transactionTemplate);
    }

    private void givenNoNotificationToday() {
        when(notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                eq(USER_ID), eq(NotificationType.RETROSPECT_DUE), any())).thenReturn(false);
    }

    private void givenCandidatePaidAt(LocalTime paidAt) {
        when(candidateService.findCandidates(eq(USER_ID), anyInt(), eq(null), eq(null)))
                .thenReturn(List.of(candidate(1043L, "○○배달", 12000, paidAt)));
    }

    private void givenUser() {
        // user()를 when(...) 안에서 만들면 스텁이 겹쳐 UnfinishedStubbingException이 난다.
        User user = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenSingleUser() {
        User user = user(USER_ID);
        when(userRepository.findAll()).thenReturn(List.of(user));
    }

    private static User user(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private static CandidateView candidate(long id, String merchant, int amount, LocalTime paidAt) {
        return new CandidateView(
                id,
                TODAY.minusDays(1).atTime(paidAt).atZone(TimeSlot.ZONE).toOffsetDateTime(),
                merchant, amount, "배달", TimeSlot.EVENING,
                ReasonCode.TIMESLOT_OUTLIER, "평소와 다른 시간대의 소비였어요.");
    }
}
