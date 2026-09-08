package kr.sottaejap.server.retrospect.service;

/** 묶음 이름(⑤). 저장 트랜잭션 커밋 후, displayName이 없는 묶음만 (E-64). AI가 없으면 템플릿 (E-38). */
public interface ClusterNamingService {

    /**
     * 이름 없는 묶음을 채운다. AI는 {@code aiClusterId} 하나에만 쓰고 나머지는 템플릿으로 짓는다 — 저장 1건이
     * 리프와 상위 묶음을 함께 만들어(롤업, E-59) 전부 AI로 지으면 저장 응답이 묶음 수 × 15초가 된다.
     *
     * @param aiClusterId AI로 이름을 지을 묶음. 저장 응답에 실리는 리프를 넘긴다
     */
    void nameUnnamed(long userId, long aiClusterId);
}
