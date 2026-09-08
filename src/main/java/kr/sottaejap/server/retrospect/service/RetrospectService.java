package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.MemoryResponse;
import kr.sottaejap.server.retrospect.dto.ReflectionView;
import kr.sottaejap.server.retrospect.dto.RetrospectChatRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectChatResponse;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveRequest;
import kr.sottaejap.server.retrospect.dto.RetrospectSaveResponse;

import java.time.LocalDate;
import java.util.List;

/** 외부 /retrospects/* 3종과 내부 AI reflections · memory의 진입점. */
public interface RetrospectService {

    /** 저장 → 전체 재계산 → 커밋 후 명명 → 리프 묶음 응답. 409 DUPLICATE_RETROSPECT · 400 INVALID_TAG · 404 NOT_FOUND. */
    RetrospectSaveResponse save(long userId, RetrospectSaveRequest request);

    CandidateListResponse candidates(long userId, Integer limit, LocalDate from, LocalDate to);

    RetrospectChatResponse chat(long userId, RetrospectChatRequest request);

    List<ReflectionView> findReflections(long userId);

    MemoryResponse memory(long userId);
}
