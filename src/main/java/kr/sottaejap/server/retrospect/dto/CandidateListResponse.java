package kr.sottaejap.server.retrospect.dto;

import java.util.List;

/** `{ "candidates": [...] }` — 비어 있으면 S10 빈 상태 (FR-03-04). */
public record CandidateListResponse(List<CandidateView> candidates) {
}
