package scu.dn.used_cars_backend.sms.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import scu.dn.used_cars_backend.config.AppSmsProperties;

import java.io.IOException;
import java.util.Map;

@Component
public class SmsHttpsFilter extends OncePerRequestFilter {

    private final AppSmsProperties smsProperties;
    private final ObjectMapper objectMapper;

    public SmsHttpsFilter(AppSmsProperties smsProperties, ObjectMapper objectMapper) {
        this.smsProperties = smsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!smsProperties.isRequireHttps()) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/sms/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.isSecure() && !"https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    Map.of("error", "HTTPS required for SMS endpoints"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
