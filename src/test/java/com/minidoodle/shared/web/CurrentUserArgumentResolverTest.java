package com.minidoodle.shared.web;

import com.minidoodle.calendar.domain.model.UserId;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parameter-acceptance guard of {@link CurrentUserArgumentResolver}.
 *
 * <p>The resolver is asked to support <em>every</em> controller method parameter, so the guard has
 * three outcomes. Two of them are exercised by the HTTP tests (a {@code @CurrentUser UserId}
 * parameter is accepted; an ordinary parameter is rejected before the annotation is even
 * consulted). The third - a parameter that carries {@code @CurrentUser} but is not a {@link UserId}
 * - cannot occur in this application, because no controller declares one, so no request can ever
 * produce it.
 *
 * <p>It is not dead code, though: it is the type check that keeps the resolver from trying to bind
 * a header to an arbitrary parameter type. Calling it directly is how that contract gets to be
 * verified rather than assumed, and it is what lets the class's branch floor sit at 1.00 instead of
 * tolerating a permanently red branch.
 */
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @Test
    void supportsACurrentUserAnnotatedUserIdParameter() {
        assertThat(resolver.supportsParameter(parameterOf("annotatedUserId"))).isTrue();
    }

    @Test
    void rejectsAParameterWithoutTheAnnotation() {
        assertThat(resolver.supportsParameter(parameterOf("unannotatedUserId"))).isFalse();
    }

    @Test
    void rejectsAnAnnotatedParameterThatIsNotAUserId() {
        assertThat(resolver.supportsParameter(parameterOf("annotatedString"))).isFalse();
    }

    private static MethodParameter parameterOf(String methodName) {
        Method method =
                Arrays.stream(ParameterProbe.class.getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();
        return new MethodParameter(method, 0);
    }

    /** The parameter shapes the guard is asked about; never invoked. */
    @SuppressWarnings("unused")
    private static final class ParameterProbe {

        void annotatedUserId(@CurrentUser UserId caller) {}

        void unannotatedUserId(UserId caller) {}

        void annotatedString(@CurrentUser String caller) {}
    }
}
