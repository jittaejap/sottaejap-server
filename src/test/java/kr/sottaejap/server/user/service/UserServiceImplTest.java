package kr.sottaejap.server.user.service;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
import kr.sottaejap.server.transaction.service.TransactionService;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.dto.UserMeResponse;
import kr.sottaejap.server.user.dto.UserSettingsRequest;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 설정 변경 (API 3 · FR-01-03,04,06) — 예산이 바뀌면 지도가 옛 예산으로 남지 않게 다시 계산한다. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private ClusterRecomputeService clusterRecomputeService;

    @InjectMocks
    private UserServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.social(AuthProvider.KAKAO, "kakao-1", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "monthlyBudget", 1_000_000);
    }

    @Test
    void 예산이_바뀌면_묶음을_다시_계산한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(transactionService.analysisYearMonth(USER_ID)).thenReturn(YearMonth.of(2026, 8));

        UserMeResponse response = service.updateSettings(USER_ID,
                new UserSettingsRequest(2_500_000, null, null));

        assertEquals(2_500_000, response.monthlyBudget());
        assertEquals("2026-08", response.analysisYearMonth());
        verify(clusterRecomputeService).recomputeAll(USER_ID);
    }

    @Test
    void 예산이_그대로면_다시_계산하지_않는다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.updateSettings(USER_ID, new UserSettingsRequest(1_000_000, 2.0, 3));

        assertEquals(2.0, user.getOutlierThreshold());
        assertEquals(3, user.getRetrospectDelayDays());
        verify(clusterRecomputeService, never()).recomputeAll(USER_ID);
    }

    @Test
    void null은_그대로_두기다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.updateSettings(USER_ID, new UserSettingsRequest(null, null, 5));

        assertEquals(1_000_000, user.getMonthlyBudget());
        assertEquals(5, user.getRetrospectDelayDays());
        verify(clusterRecomputeService, never()).recomputeAll(USER_ID);
    }

    @Test
    void 없는_사용자는_404다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateSettings(USER_ID, new UserSettingsRequest(2_500_000, null, null)));
        assertEquals(CommonErrorCode.NOT_FOUND, exception.getErrorCode());
    }
}
