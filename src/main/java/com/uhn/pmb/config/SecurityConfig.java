package com.uhn.pmb.config;

import com.uhn.pmb.security.JwtAuthenticationFilter;
import com.uhn.pmb.security.JwtAuthenticationEntryPoint;
import com.uhn.pmb.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // ✅ FIX: Don't use wildcard with credentials - specify explicit origins instead
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * ✅ EXCLUDE STATIC RESOURCES FROM SECURITY FILTER
     * - These assets bypass Spring Security entirely
     * - No authentication needed for static files
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
            .requestMatchers("/static/**")
            .requestMatchers("/favicon.ico")
            .requestMatchers("/logo/**")
            .requestMatchers("/gambar/**")
            .requestMatchers("/images/**")
            .requestMatchers("/components/**")
            .requestMatchers("/uploads/**")
            .requestMatchers("/css/**")
            .requestMatchers("/js/**")
            .requestMatchers("/*.ico")
            .requestMatchers("/*.js")
            .requestMatchers("/*.css")
            .requestMatchers("/*.json")
            .requestMatchers("/*.png")
            .requestMatchers("/*.jpg")
            .requestMatchers("/*.svg");
    }

    /**
     * ✅ SECURITY: Minimal & User-Friendly
     * - All pages public (frontend JS handles auth check)
     * - Only API endpoints require backend authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("🔥 [SECURITY] CONFIG LOADED");
        
        http
            .authenticationProvider(authenticationProvider())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    System.out.println("╔════════════════════════════════════════════╗");
                    System.out.println("❌ [AUTH ENTRY POINT - NOT AUTHENTICATED]");
                    System.out.println("   PATH: " + req.getRequestURI());
                    System.out.println("   METHOD: " + req.getMethod());
                    System.out.println("   ERROR: " + e.getMessage());
                    System.out.println("╚════════════════════════════════════════════╝");
                    res.sendError(401, "Unauthorized");
                })
                .accessDeniedHandler((req, res, e) -> {
                    System.out.println("╔════════════════════════════════════════════╗");
                    System.out.println("🚫 [ACCESS DENIED - NO PERMISSION]");
                    System.out.println("   PATH: " + req.getRequestURI());
                    System.out.println("   METHOD: " + req.getMethod());
                    System.out.println("   ERROR: " + e.getMessage());
                    System.out.println("╚════════════════════════════════════════════╝");
                    res.sendError(403, "Forbidden");
                })
            )
            .authorizeHttpRequests(authorize -> authorize
                // ========== CORS PREFLIGHT: Always allow OPTIONS ==========
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // ========== H2 CONSOLE (Development) ==========
                .requestMatchers("/h2-console/**").permitAll()
                
                // ========== STATIC RESOURCES: ALL STATIC FILES MUST BE PUBLIC ==========
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/components/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/logo/**").permitAll()
                .requestMatchers("/gambar/**").permitAll()
                .requestMatchers("/images/**").permitAll()
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/*.js", "/*.css", "/*.json", "/*.ico", "/*.png").permitAll()
                
                // ========== ALL PAGES PUBLIC (Frontend handles auth) ==========
                .requestMatchers("/").permitAll()
                .requestMatchers("/index.html").permitAll()
                .requestMatchers("/login.html", "/register.html").permitAll()
                .requestMatchers("/*.html").permitAll()
                .requestMatchers("/dashboard-*.html").permitAll()
                .requestMatchers("/admin-*.html").permitAll()
                .requestMatchers("/form-pendaftaran.html").permitAll()
                .requestMatchers("/api/files/**").permitAll()
                .requestMatchers("/error").permitAll()
                
                // ========== PAGE CONTROLLER ROUTES (Frontend handles auth) ==========
                .requestMatchers("/admin/dashboard-*").permitAll()
                .requestMatchers("/admin/reenroll").permitAll()
                
                // ========== PUBLIC API - No auth needed ==========
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/camaba/**").permitAll()        // ✅ PUBLIC: Gelombang selection, profile, etc (optional JWT)
                .requestMatchers("/api/students/all").permitAll()
                .requestMatchers("/api/files/**").permitAll()
                .requestMatchers("/api/cicilan/**").permitAll()      // ✅ Cicilan endpoints (auth checked in controller)
                .requestMatchers("/jenis-seleksi/**").permitAll()     // ✅ PUBLIC: JENIS_SELEKSI data (needed for formula mapping)
                .requestMatchers("/api/sma/**").permitAll()           // ✅ PUBLIC: SMA search for autocomplete in form-pendaftaran
                
                // ========== ADMIN ENDPOINTS: AUTHENTICATION REQUIRED (role checks done via @PreAuthorize) ==========
                // /admin/periods - requires authentication (role access controlled by @PreAuthorize in controller)
                .requestMatchers(HttpMethod.GET, "/admin/periods").authenticated()
                .requestMatchers(HttpMethod.GET, "/admin/periods/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/periods").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/periods/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/admin/periods/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/admin/periods/**").authenticated()
                
                // /admin/jenis-seleksi - requires authentication (role access controlled by @PreAuthorize in controller)
                .requestMatchers(HttpMethod.GET, "/admin/jenis-seleksi").authenticated()
                .requestMatchers(HttpMethod.GET, "/admin/jenis-seleksi/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/jenis-seleksi").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/jenis-seleksi/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/admin/jenis-seleksi/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/admin/jenis-seleksi/**").authenticated()
                
                // ========== PROTECTED API: All methods require JWT authentication ==========
                // /api/validasi/** - ADMIN_VALIDASI endpoints
                .requestMatchers(HttpMethod.GET, "/api/validasi/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/validasi/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/validasi/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/validasi/**").authenticated()
                
                // /admin/api/** - All ADMIN endpoints (various roles)
                .requestMatchers(HttpMethod.GET, "/admin/api/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/admin/api/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/admin/api/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/admin/api/**").authenticated()
                
                // /api/admin/** - Alternative admin API paths
                .requestMatchers(HttpMethod.GET, "/api/admin/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/admin/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/admin/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/admin/**").authenticated()
                
                // Reminders and other protected endpoints
                .requestMatchers(HttpMethod.POST, "/api/send-reminder").authenticated()
                
                // ========== Everything else: Require authentication ==========
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        // ========== JWT FILTER FOR TOKEN PROCESSING ==========
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
