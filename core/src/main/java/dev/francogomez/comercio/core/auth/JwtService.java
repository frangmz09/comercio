package dev.francogomez.comercio.core.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;


/**
 * Emite y valida los tokens. Son stateless: el servidor no guarda sesiones, y toda la
 * información necesaria para autorizar viaja firmada dentro del token.
 *
 * <p>La contracara de eso es que un token emitido no se puede revocar antes de que
 * expire. Por eso la vigencia es corta: es el único mecanismo real de caducidad que hay
 * sin agregar una lista de revocación, que traería de vuelta el estado que este esquema
 * viene a evitar.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final Duration vigencia;

    public JwtService(@Value("${comercio.jwt.secret}") String secret,
                      @Value("${comercio.jwt.vigencia-minutos:60}") long vigenciaMinutos) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 exige al menos 256 bits. Fallar en el arranque es preferible a
            // levantar con una firma débil que nadie note hasta que alguien la rompa.
            throw new IllegalStateException(
                    "comercio.jwt.secret debe tener al menos 32 caracteres; tiene " + bytes.length);
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
    }

    public String emitirPara(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(vigencia)))
                .signWith(clave)
                .compact();
    }

    /**
     * Verifica firma y vigencia.
     *
     * <p>El resultado distingue vencido de inválido en lugar de colapsar los dos en un
     * {@code Optional} vacío, porque para quien consume la API son problemas distintos:
     * el token vencido se arregla pidiendo otro, el inválido revisando qué se está
     * mandando en el header. Un 401 que no lo aclara deja al cliente adivinando.
     */
    public Verificacion verificar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new Verificacion.Valido(new DatosToken(
                    claims.getSubject(),
                    Rol.valueOf(claims.get("rol", String.class))));
        } catch (ExpiredJwtException e) {
            return new Verificacion.Vencido();
        } catch (JwtException | IllegalArgumentException e) {
            // Firma adulterada, formato roto, o un rol que dejó de existir en el enum.
            return new Verificacion.Invalido();
        }
    }

    public long getVigenciaSegundos() {
        return vigencia.toSeconds();
    }

    public record DatosToken(String username, Rol rol) {
    }

    /**
     * Resultado de verificar un token. Sellada a propósito: son los tres casos posibles
     * y el filtro los cubre con un switch exhaustivo, sin rama por defecto que tape un
     * caso nuevo si mañana se agrega uno.
     */
    public sealed interface Verificacion {

        record Valido(DatosToken datos) implements Verificacion {
        }

        record Vencido() implements Verificacion {
        }

        record Invalido() implements Verificacion {
        }
    }
}
