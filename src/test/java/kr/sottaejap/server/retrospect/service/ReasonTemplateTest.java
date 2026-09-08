package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.ReasonCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** reasonCode당 한 문장 (E-62). 코드를 늘리면 문장도 늘어야 한다. */
class ReasonTemplateTest {

    @Test
    void 모든_reasonCode에_서로_다른_문장이_있다() {
        Set<String> sentences = Arrays.stream(ReasonCode.values())
                .map(ReasonTemplate::reasonFor)
                .collect(Collectors.toSet());

        assertEquals(ReasonCode.values().length, sentences.size());
        assertFalse(sentences.stream().anyMatch(String::isBlank));
    }
}
