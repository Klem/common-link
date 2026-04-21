package org.commonlink.security

import jakarta.servlet.http.HttpServletResponse
import org.commonlink.entity.UserRole
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security configuration for the CommonLink API.
 *
 * Key security decisions:
 * - **Stateless sessions**: no HTTP session is created; authentication is entirely JWT-based.
 * - **CSRF disabled**: safe for a stateless REST API that does not use cookies for auth.
 * - **CORS**: restricted to the configured `app.frontend-url` origin only.
 * - **Route-level authorization**: `/api/auth / **` and `/api/docs / **` are public; association and
 *   donor routes are role-gated; all other routes require a valid JWT.
 * - **BCrypt cost factor 12**: balances security (offline attack resistance) with login latency.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val userDetailsService: UserDetailsServiceImpl,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) {

    /**
     * Defines the HTTP security filter chain.
     *
     * Route access rules:
     * - `/api/auth / **`, `/api/docs / **` — public (no token required).
     * - `/api/association / **` — requires `ROLE_ASSOCIATION`.
     * - `/api/donor / **` — requires `ROLE_DONOR`.
     * - Everything else — requires any valid JWT (any role).
     *
     * [JwtAuthenticationFilter] is inserted before the standard
     * [UsernamePasswordAuthenticationFilter] so JWT auth runs first.
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    .requestMatchers("/api/auth/**", "/api/docs/**").permitAll()
                    // Optional: protect other actuator endpoints
//                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .requestMatchers("/api/association/**").hasRole(UserRole.ASSOCIATION.toString())
                    .requestMatchers("/api/donor/**").hasRole(UserRole.DONOR.toString())
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

            // === This is the key addition for clean 401 vs 403 ===
            .exceptionHandling { exceptions ->
                exceptions
                    // Missing / invalid / expired token → 401
                    .authenticationEntryPoint { _, response, _ ->
                        response.status = HttpServletResponse.SC_UNAUTHORIZED
                    }
                    // Authenticated but wrong role → 403
                    .accessDeniedHandler { _, response, _ ->
                        response.status = HttpServletResponse.SC_FORBIDDEN
                    }
            }

        return http.build()
    }

    /**
     * Configures CORS to allow requests only from the configured frontend URL.
     *
     * Credentials (`allowCredentials = true`) are enabled to support cookie-based flows
     * if they are introduced in the future. The `Authorization` header is exposed so the
     * frontend can read it in JavaScript (e.g. for token rotation).
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf(frontendUrl)
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type")
        config.exposedHeaders = listOf("Authorization")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    /**
     * BCrypt password encoder with cost factor 12.
     *
     * Used when hashing passwords on registration and when verifying them on email/password login.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    /**
     * DAO authentication provider wiring [UserDetailsServiceImpl] and the [passwordEncoder].
     *
     * Used by [AuthenticationManager] for form-based or programmatic authentication flows.
     */
    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    /**
     * Exposes Spring's [AuthenticationManager] as a bean so it can be injected into services
     * that need to perform programmatic authentication (e.g. login via username/password).
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
