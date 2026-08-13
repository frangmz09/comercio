package dev.francogomez.comercio.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea los usuarios de la demo si todavía no existen.
 *
 * <p>Va acá y no en una migración por una razón concreta: un INSERT en el SQL exigiría
 * dejar un hash BCrypt escrito en el repositorio, y ese hash sería el mismo para
 * cualquiera que clone el proyecto. Generándolo en el arranque, al menos el valor
 * almacenado es distinto en cada instalación.
 *
 * <p>Igual son credenciales de demostración y están publicadas en el README: el punto no
 * es que sean secretas, sino que la API no quede abierta a escritura anónima. En un
 * sistema real esto se apaga con {@code comercio.demo.seed-usuarios=false} y los
 * usuarios se dan de alta por otro camino.
 */
@Configuration
@ConditionalOnProperty(name = "comercio.demo.seed-usuarios", havingValue = "true", matchIfMissing = true)
public class SeedUsuarios {

    private static final Logger log = LoggerFactory.getLogger(SeedUsuarios.class);

    @Bean
    public ApplicationRunner crearUsuariosDeDemo(UsuarioRepository usuarios, PasswordEncoder encoder) {
        return args -> {
            crearSiFalta(usuarios, encoder, "admin", "admin123", Rol.ADMIN);
            crearSiFalta(usuarios, encoder, "vendedor", "vendedor123", Rol.VENDEDOR);
        };
    }

    private void crearSiFalta(UsuarioRepository usuarios, PasswordEncoder encoder,
                              String username, String password, Rol rol) {
        if (usuarios.existsByUsername(username)) {
            return;
        }
        usuarios.save(new Usuario(username, encoder.encode(password), rol));
        log.info("Usuario de demo '{}' creado con rol {}", username, rol);
    }
}
