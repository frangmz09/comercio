package dev.francogomez.comercio.core.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Obtención del token de acceso")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener un token",
            description = """
                    Devuelve un JWT para enviar como `Authorization: Bearer <token>`.

                    En la demo pública hay dos usuarios cargados: `admin` / `admin123` con rol
                    ADMIN, y `vendedor` / `vendedor123` con rol VENDEDOR. Consultar no requiere
                    token; modificar sí.""")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return usuarios.findByUsernameAndActivoTrue(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .map(u -> ResponseEntity.ok(new LoginResponse(
                        jwtService.emitirPara(u),
                        "Bearer",
                        jwtService.getVigenciaSegundos(),
                        u.getUsername(),
                        u.getRol())))
                // El mismo mensaje para usuario inexistente y contraseña incorrecta: decir
                // cuál de las dos falló le confirma a un atacante qué usuarios existen.
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario o contraseña incorrectos"));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            String tipo,
            long expiraEnSegundos,
            String username,
            Rol rol) {
    }
}
