package kr.sottaejap.server.internalai;

import kr.sottaejap.server.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `/internal/ai/*`는 사람이 아니라 AI의 `SpringClient`가 부른다 (05 §3).
 *
 * <p>그쪽 `_request`는 봉투를 벗긴 뒤 <b>`data`가 JSON object가 아니면 예외</b>를 던진다
 * (AI 레포 `app/clients/spring_client.py`). 그런데 `ApiResponse`는 `@JsonInclude(NON_NULL)`이라
 * {@code ApiResponse<Void>}로 성공을 내리면 `data` 키가 응답에서 통째로 빠진다.
 * 그러면 AI가 500을 내고 Spring은 그것을 503 `LLM_UNAVAILABLE`로 보여준다 — LLM은 멀쩡한데
 * 저장만 실패하는, 원인을 찾기 어려운 증상이다.
 *
 * <p>그래서 이 컨트롤러의 핸들러는 성공 응답에 항상 object를 싣는다. 돌려줄 것이 없으면
 * {@code Void}가 아니라 빈 Map을 쓴다.
 */
class InternalAiContractTest {

    @Test
    void handlersNeverReturnVoidData() {
        List<Method> handlers = Arrays.stream(InternalAiController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RequestMapping.class)
                        || Arrays.stream(method.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType()
                                .isAnnotationPresent(RequestMapping.class)))
                .toList();

        assertThat(handlers).as("내부 AI API 6종이 있어야 한다").hasSize(6);

        for (Method handler : handlers) {
            Type returnType = handler.getGenericReturnType();
            assertThat(returnType)
                    .as("%s는 ApiResponse<T>를 돌려줘야 한다", handler.getName())
                    .isInstanceOf(ParameterizedType.class);

            ParameterizedType parameterized = (ParameterizedType) returnType;
            assertThat(parameterized.getRawType()).isEqualTo(ApiResponse.class);
            assertThat(parameterized.getActualTypeArguments()[0])
                    .as("%s: data가 없는 200은 AI 쪽에서 예외가 된다. Void 대신 빈 Map을 쓴다",
                            handler.getName())
                    .isNotEqualTo(Void.class);
        }
    }
}
