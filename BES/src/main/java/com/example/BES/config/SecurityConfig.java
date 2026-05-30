package com.example.BES.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${BES_ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${BES_EMCEE_PASSWORD}")
    private String emceePassword;

    @Value("${BES_JUDGE_PASSWORD}")
    private String judgePassword;

    @Value("${BES_ORGANISER_PASSWORD}")
    private String organiserPassword;

    @Value("${BES_COOKIE_DOMAIN:localhost}")
    private String cookieDomain;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        return http
        // to enable again if needed
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/battle/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/results").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/config/app").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .securityContext(Customizer.withDefaults())
                    .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                    .build();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails admin = User.builder()
            .username("admin")
            .password(pwdEncoder().encode(adminPassword))
            .roles("ADMIN")
            .build();
        UserDetails emcee = User.builder()
            .username("emcee")
            .password(pwdEncoder().encode(emceePassword))
            .roles("EMCEE")
            .build();
        UserDetails judge = User.builder()
            .username("judge")
            .password(pwdEncoder().encode(judgePassword))
            .roles("JUDGE")
            .build();
        UserDetails organiser = User.builder()
            .username("organiser")
            .password(pwdEncoder().encode(organiserPassword))
            .roles("ORGANISER")
            .build();
        return new InMemoryUserDetailsManager(admin, emcee, judge, organiser);
    }

    @Bean
    public PasswordEncoder pwdEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration authConfig) throws Exception{
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("None");
        serializer.setUseSecureCookie(true);
        serializer.setDomainName(cookieDomain);
        return serializer;
    }
}
