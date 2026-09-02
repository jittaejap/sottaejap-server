package kr.sottaejap.server.ai.dto;

import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.TaskType;

import java.util.Map;

/**
 * 05 §3 task_context. status는 Spring이 소유하고 AI는 바꾸지 않는다. state 구조는 task별로 05 §3 표가 정본이다.
 */
public record TaskContext(TaskType task, RetrospectStatus status, Map<String, Object> state) {
}
