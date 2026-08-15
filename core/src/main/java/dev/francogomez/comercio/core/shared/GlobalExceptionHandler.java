package dev.francogomez.comercio.core.shared;

import dev.francogomez.comercio.core.auth.CredencialesInvalidasException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Traduce las excepciones de dominio y de framework a {@link ApiError}. Todo error que
 * cruza esta API tiene la misma forma, sin importar dónde se originó.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> handleCredenciales(CredencialesInvalidasException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.merge(fe.getField(), fe.getDefaultMessage(), (a, b) -> a + "; " + b));

        ApiError body = ApiError.validation(
                HttpStatus.BAD_REQUEST.value(),
                "Uno o más campos no son válidos",
                req.getRequestURI(),
                errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es JSON válido", req);
    }

    /**
     * Falta un header obligatorio (por ejemplo {@code Idempotency-Key} al registrar una
     * venta). Es un error del cliente, no del servidor.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleHeaderFaltante(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta el header obligatorio '%s'".formatted(ex.getHeaderName()), req);
    }

    /** Validaciones sobre parámetros y headers, que no pasan por el binding del body. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleViolaciones(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.put(String.valueOf(v.getPropertyPath()), v.getMessage()));

        ApiError body = ApiError.validation(
                HttpStatus.BAD_REQUEST.value(),
                "Uno o más parámetros no son válidos",
                req.getRequestURI(),
                errors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Dos transacciones que compitieron por lo mismo y perdió esta. No es un fallo del
     * servidor: el pedido llegó bien y chocó con el estado que dejó otro. Devolverlo como
     * 500 le diría al cliente que algo se rompió, cuando lo correcto es que reintente.
     *
     * <p>Los servicios ya interceptan las carreras que saben nombrar —el solapamiento de
     * precios, la clave de idempotencia repetida— y las traducen con un mensaje de
     * dominio. Esto es la red para las que no: el SKU duplicado que dos altas simultáneas
     * pasan las dos, o la segunda nota de crédito sobre la misma venta.
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiError> handleChoqueDeConcurrencia(Exception ex, HttpServletRequest req) {
        log.warn("Choque de concurrencia en {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT,
                "La operación chocó con otra simultánea sobre los mismos datos; reintentá", req);
    }

    /**
     * SQLState de PostgreSQL que corresponden a una regla del esquema que el pedido no
     * respeta: unicidad, exclusión de rangos y CHECK. Los tres describen un conflicto con
     * el estado de los datos, no un error del servidor.
     *
     * <p>Quedan afuera a propósito los demás de la clase 23 —un NOT NULL o una clave
     * foránea que no resuelve son un defecto del código, y esos sí tienen que salir como
     * 500 y quedar en el log.
     */
    private static final Set<String> CONFLICTOS_DE_ESQUEMA = Set.of("23505", "23P01", "23514");

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        if (!violaUnaReglaDelEsquema(ex)) {
            return handleUnexpected(ex, req);
        }
        return handleChoqueDeConcurrencia(ex, req);
    }

    private static boolean violaUnaReglaDelEsquema(DataIntegrityViolationException ex) {
        return ex.getMostSpecificCause() instanceof SQLException sql
                && CONFLICTOS_DE_ESQUEMA.contains(sql.getSQLState());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno inesperado", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
