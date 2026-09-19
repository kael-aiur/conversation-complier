package site.kael.conversationcompiler.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AgentTraceAuthInterceptor implements HandlerInterceptor {
    private final String configuredKey;

    public AgentTraceAuthInterceptor(@Value("${agent-trace.auth-key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (configuredKey.isBlank()) return true;
        String authorization = request.getHeader("Authorization");
        if (! ("Bearer " + configuredKey).equals(authorization)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }
}
