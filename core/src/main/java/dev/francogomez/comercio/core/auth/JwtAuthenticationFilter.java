package dev.francogomez.comercio.core.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el token del header {@code Authorization} y, si es válido, deja la autenticación
 * en el contexto para que las reglas de autorización puedan evaluarla.
 *
 * <p>Un token ausente o inválido no corta la cadena: simplemente no autentica y sigue.
 * Quien decide si eso alcanza es la configuración de seguridad, no este filtro — así las
 * rutas públicas siguen funcionando sin tener que enumerarlas también acá.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        tokenDe(request)
                .flatMap(jwtService::validar)
                .ifPresent(datos -> autenticar(datos, request));

        filterChain.doFilter(request, response);
    }

    private java.util.Optional<String> tokenDe(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIJO)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(header.substring(PREFIJO.length()).trim());
    }

    private void autenticar(JwtService.DatosToken datos, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                datos.username(),
                null, // las credenciales ya se verificaron al emitir el token
                List.of(new SimpleGrantedAuthority(datos.rol().comoAuthority())));

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
