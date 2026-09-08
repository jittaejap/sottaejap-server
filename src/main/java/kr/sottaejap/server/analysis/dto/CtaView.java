package kr.sottaejap.server.analysis.dto;

import kr.sottaejap.server.common.enums.CtaType;

/** 상세 패널 CTA (E-76). 라벨은 서버가 정한다 — 화면마다 다른 문구가 생기지 않게. */
public record CtaView(CtaType type, String label) {
}
