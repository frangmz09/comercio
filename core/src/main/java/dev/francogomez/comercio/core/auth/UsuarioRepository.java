package dev.francogomez.comercio.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    boolean existsByUsername(String username);
}
