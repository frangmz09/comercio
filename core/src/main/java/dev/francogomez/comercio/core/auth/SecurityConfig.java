package dev.francogomez.comercio.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.francogomez.comercio.core.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // hasRole() agrega el prefijo ROLE_ por su cuenta, asi que acá va el nombre pelado.
    private static final String ADMIN = Rol.ADMIN.name();
    private static final String VENDEDOR = Rol.VENDEDOR.name();

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * Lectura abierta, escritura autenticada.
     *
     * <p>La decisión es deliberada y tiene que ver con para qué existe este despliegue:
     * es una demo pública, y exigir login para mirar el catálogo la volvería inútil para
     * quien la abre por primera vez. Todo lo que modifica estado —vender, mover stock,
     * tocar precios— sí requiere token.
     *
     * <p>Las reglas van por ruta y no con {@code @PreAuthorize} sobre los servicios: la
     * autorización es una preocupación del borde HTTP, y dejarla ahí mantiene a los
     * servicios y a sus tests libres de contexto de seguridad.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Sin cookies ni sesión no hay CSRF que prevenir: el token viaja en un
                // header que el navegador no adjunta solo.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()

                        // Consultar no requiere token: es lo que hace navegable la demo.
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()

                        // Los roles salen del enum y no de literales sueltos: renombrar
                        // uno rompe la compilación en vez de dejar una regla que ya no
                        // coincide con nada y silenciosamente niega el acceso a todos.
                        .requestMatchers("/api/v1/productos/**").hasRole(ADMIN)
                        .requestMatchers("/api/v1/listas-precio/**").hasRole(ADMIN)
                        .requestMatchers("/api/v1/puntos-venta/**").hasRole(ADMIN)

                        // Operar sobre el mostrador: cualquiera de los dos roles.
                        .requestMatchers("/api/v1/ventas/**").hasAnyRole(ADMIN, VENDEDOR)
                        .requestMatchers("/api/v1/stock/**").hasAnyRole(ADMIN, VENDEDOR)

                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Sin esto Spring responde con una página HTML de error. Devolver el mismo
     * {@link ApiError} que el resto de la API vale la pena: un cliente que ya sabe
     * parsear los errores no necesita un caso especial para el 401.
     */
    private AuthenticationEntryPoint entryPoint() {
        return (request, response, authException) ->
                escribirError(request, response, HttpStatus.UNAUTHORIZED,
                        "Se requiere autenticación: enviá el token en el header Authorization");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                escribirError(request, response, HttpStatus.FORBIDDEN,
                        "Tu rol no tiene permiso para esta operación");
    }

    private void escribirError(HttpServletRequest request, HttpServletResponse response,
                               HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(status.value(), status.getReasonPhrase(), mensaje, request.getRequestURI()));
    }
}
