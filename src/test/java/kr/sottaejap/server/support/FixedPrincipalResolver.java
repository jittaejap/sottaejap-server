package kr.sottaejap.server.support;

import kr.sottaejap.server.auth.security.AuthenticatedUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * standaloneSetup에는 Security 컨텍스트가 없어 {@code @AuthenticationPrincipal}이 null로 들어온다.
 * 컨트롤러 테스트마다 같은 리졸버를 다시 쓰지 않도록 여기 하나만 둔다.
 */
public final class FixedPrincipalResolver implements HandlerMethodArgumentResolver {

    private final long userId;

    public FixedPrincipalResolver(long userId) {
        this.userId = userId;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return new AuthenticatedUser(userId);
    }
}
