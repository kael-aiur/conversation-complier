package site.kael.conversationcompiler.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentTraceAuthInterceptorTest {
    @Test
    void allowsRequestsWhenAuthIsNotConfigured() throws Exception {
        var interceptor = new AgentTraceAuthInterceptor("");
        assertThat(interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void rejectsMissingOrWrongBearerToken() throws Exception {
        var interceptor = new AgentTraceAuthInterceptor("ast_test");
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong");
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void acceptsConfiguredBearerToken() throws Exception {
        var interceptor = new AgentTraceAuthInterceptor("ast_test");
        var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer ast_test");
        assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object())).isTrue();
    }
}
