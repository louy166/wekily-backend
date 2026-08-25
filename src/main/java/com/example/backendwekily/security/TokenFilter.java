package com.example.backendwekily.security;

import com.example.backendwekily.entity.AgentToken;
import com.example.backendwekily.repository.AgentTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenFilter extends OncePerRequestFilter {

    private final AgentTokenRepository agentTokenRepository;

    @Override
    // ✅ PAS de @Transactional ici — cause crash au démarrage
    // ✅ AgentToken.agent est EAGER → pas besoin de session
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        // ✅ Ignorer les routes admin — elles ont leur propre auth
        String path = request.getRequestURI();
        if (path.startsWith("/admin/") || path.startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String rawToken = authHeader.substring(7).trim();
            try {
                Optional<AgentToken> opt = agentTokenRepository.findByToken(rawToken);
                if (opt.isPresent()) {
                    // ✅ EAGER → agent déjà chargé, pas de LazyInitializationException
                    String email = opt.get().getAgent().getEmail();
                    var auth = new UsernamePasswordAuthenticationToken(
                            email, null,
                            List.of(new SimpleGrantedAuthority("ROLE_AGENT"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("✅ Token OK — {}", email);
                } else {
                    log.warn("❌ Token invalide");
                }
            } catch (Exception e) {
                log.error("TokenFilter erreur: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}