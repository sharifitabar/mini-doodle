package com.minidoodle.shared.web;

import com.minidoodle.calendar.domain.model.UserId;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser}-annotated {@link UserId} parameters from the {@code X-User-Id}
 * header (demo authentication).
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserId.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String raw = webRequest.getHeader(USER_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new MissingCallerIdException();
        }
        try {
            return UserId.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            throw new MalformedCallerIdException(raw);
        }
    }
}
