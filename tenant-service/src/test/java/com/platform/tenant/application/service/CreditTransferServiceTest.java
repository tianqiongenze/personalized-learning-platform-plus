package com.platform.tenant.application.service;

import com.platform.tenant.application.command.ApplyCreditTransferCommand;
import com.platform.tenant.domain.model.CreditTransferRule;
import com.platform.tenant.domain.repository.CreditTransferRecord;
import com.platform.tenant.domain.repository.CreditTransferRecordRepository;
import com.platform.tenant.domain.repository.CreditTransferRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditTransferService 测试")
class CreditTransferServiceTest {

    private static final String SOURCE_TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET_TENANT = "22222222-2222-2222-2222-222222222222";
    private static final String STUDENT_ID = "33333333-3333-3333-3333-333333333333";

    @Mock
    private CreditTransferRuleRepository ruleRepository;

    @Mock
    private CreditTransferRecordRepository recordRepository;

    @InjectMocks
    private CreditTransferService creditTransferService;

    private CreditTransferRule activeRule(String ratio, int minScore) {
        return CreditTransferRule.reconstruct(
            "44444444-4444-4444-4444-444444444444",
            SOURCE_TENANT, TARGET_TENANT,
            "CS101", "CS201",
            new BigDecimal(ratio), minScore, "ACTIVE", 0L
        );
    }

    @Nested
    @DisplayName("规则匹配")
    class RuleMatching {

        @Test
        @DisplayName("匹配到 ACTIVE 规则且分数达标时应折算学分并写入记录")
        void shouldGrantCreditsWhenRuleMatches() {
            // Given
            when(ruleRepository.findActiveBySource(SOURCE_TENANT, "CS101"))
                .thenReturn(Optional.of(activeRule("1.0", 60)));

            // When
            BigDecimal granted = creditTransferService.apply(
                new ApplyCreditTransferCommand(STUDENT_ID, SOURCE_TENANT, "CS101", 85));

            // Then
            assertThat(granted).isEqualByComparingTo(new BigDecimal("0.85"));
            ArgumentCaptor<CreditTransferRecord> captor =
                ArgumentCaptor.forClass(CreditTransferRecord.class);
            verify(recordRepository).save(captor.capture());
            assertThat(captor.getValue().studentId()).isEqualTo(STUDENT_ID);
            assertThat(captor.getValue().ruleId()).isEqualTo("44444444-4444-4444-4444-444444444444");
        }

        @Test
        @DisplayName("无匹配规则时应抛出 CreditTransferRuleNotFoundException")
        void shouldThrowWhenNoRuleMatches() {
            // Given
            when(ruleRepository.findActiveBySource(SOURCE_TENANT, "CS404"))
                .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> creditTransferService.apply(
                new ApplyCreditTransferCommand(STUDENT_ID, SOURCE_TENANT, "CS404", 90)))
                .isInstanceOf(CreditTransferRuleNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("分数不足拒绝")
    class ScoreRejection {

        @Test
        @DisplayName("分数低于 min_score 时应抛出 IllegalStateException 且不写记录")
        void shouldRejectWhenScoreBelowMin() {
            // Given
            when(ruleRepository.findActiveBySource(SOURCE_TENANT, "CS101"))
                .thenReturn(Optional.of(activeRule("1.0", 60)));

            // When & Then
            assertThatThrownBy(() -> creditTransferService.apply(
                new ApplyCreditTransferCommand(STUDENT_ID, SOURCE_TENANT, "CS101", 55)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("最低分数");
            verifyNoInteractions(recordRepository);
        }
    }

    @Nested
    @DisplayName("比例折算")
    class RatioCalculation {

        @Test
        @DisplayName("credit_ratio=2.0 时应按比例折算授予学分")
        void shouldApplyCreditRatio() {
            // Given: ratio 2.0, score 80 -> 2.0 * 80 / 100 = 1.60
            when(ruleRepository.findActiveBySource(SOURCE_TENANT, "CS101"))
                .thenReturn(Optional.of(activeRule("2.0", 60)));

            // When
            BigDecimal granted = creditTransferService.apply(
                new ApplyCreditTransferCommand(STUDENT_ID, SOURCE_TENANT, "CS101", 80));

            // Then
            assertThat(granted).isEqualByComparingTo(new BigDecimal("1.60"));
        }
    }
}
