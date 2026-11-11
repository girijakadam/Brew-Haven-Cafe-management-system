package com.brewhaven.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CustomUserDetailsService customUserDetailsService) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromJWT(jwt);
                String roleClaim = tokenProvider.getRoleFromJWT(jwt);

                if (username != null && roleClaim != null) {
                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                    boolean roleMatches = userDetails.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals(roleClaim));

                    if (roleMatches) {
                        if (userDetails instanceof UserPrincipal up) {
                            java.util.Date issuedAt = tokenProvider.getIssuedAtFromJWT(jwt);
                            if (issuedAt != null && up.getPasswordChangedAt() != null) {
                                long issuedAtEpoch = issuedAt.getTime() / 1000;
                                long changedAtEpoch = up.getPasswordChangedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                                if (issuedAtEpoch < changedAtEpoch - 1) {
                                    logger.warn("JWT token was issued before password reset for '{}'. Token invalidated.", username);
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json");
                                    response.getWriter().write("{\"success\":false,\"message\":\"Session has been invalidated due to a password reset. Please log in again.\"}");
                                    return;
                                }
                            }

                            if (up.isMustChangePassword()) {
                                String uri = request.getRequestURI();
                                boolean isAuthEndpoint = uri.startsWith("/api/auth/") || uri.equals("/api/auth");
                                boolean isPublicGet = request.getMethod().equalsIgnoreCase("GET") && (uri.startsWith("/api/menu") || uri.startsWith("/api/tables"));
                                if (!isAuthEndpoint && !isPublicGet) {
                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    response.setContentType("application/json");
                                    response.getWriter().write("{\"success\":false,\"message\":\"Password change required. You must change your temporary password before accessing cafe services.\",\"mustChangePassword\":true}");
                                    return;
                                }
                            }
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        logger.warn("JWT role claim '{}' does not match user authorities for '{}'", roleClaim, username);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
