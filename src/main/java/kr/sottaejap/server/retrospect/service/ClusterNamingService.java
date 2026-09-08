package kr.sottaejap.server.retrospect.service;

/** 묶음 이름(⑤). 저장 트랜잭션 커밋 후, displayName이 없는 묶음만 (E-64). AI가 없으면 템플릿 (E-38). */
public interface ClusterNamingService {

    void nameUnnamed(long userId);
}
