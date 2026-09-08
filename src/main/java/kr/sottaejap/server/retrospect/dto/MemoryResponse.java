package kr.sottaejap.server.retrospect.dto;

import java.util.List;

/** 내부 AI `get_memory` (05 §3) — 개인 소비 메모리 요약. */
public record MemoryResponse(List<ClusterMemoryView> clusters, List<ReflectionView> recentReflections) {
}
