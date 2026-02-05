package health.zaed.identity.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import health.zaed.identity.exception.InvalidTokenException;
import health.zaed.identity.service.JwtService;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that validates tokens and sets up SecurityContext.
 *
 * <p>Supports multiple token types:
 * <ul>
 *   <li>access - Standard user access tokens</li>
 *   <li>temp - Temporary tokens for OTP-verified users</li>
 *   <li>2fa_pending - Tokens awaiting 2FA verification</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.validateToken(token);
                String tokenType = jwtService.getTokenType(claims);

                if (!"access".equals(tokenType) && !"temp".equals(tokenType)) {
                    log.debug("Invalid token type for authentication: {}", tokenType);
                    filterChain.doFilter(request, response);
                    return;
                }

                AuthPrincipal principal = buildPrincipal(claims, tokenType);
                List<SimpleGrantedAuthority> authorities = buildAuthorities(claims);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated: {} with type {}", principal.subject(), tokenType);

            } catch (InvalidTokenException e) {
                log.debug("Token validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private AuthPrincipal buildPrincipal(Claims claims, String tokenType) {
        return new AuthPrincipal(
            claims.getSubject(),
            tokenType,
            claims.get("role", String.class),
            claims.get("partnerId", String.class),
            claims.get("context", String.class),
            claims.get("referenceId", String.class),
            claims.get("trackingCode", String.class)
        );
    }

    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> buildAuthorities(Claims claims) {
        List<String> permissions = claims.get("permissions", List.class);
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/otp") ||
               path.startsWith("/api/v1/auth/login") ||
               path.startsWith("/actuator") ||
               path.equals("/api/v1/auth/refresh");
    }
}
