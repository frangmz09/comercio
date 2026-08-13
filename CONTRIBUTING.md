# Cómo se trabaja en este repositorio

Es un proyecto de una sola persona, pero el proceso está armado como si no lo fuera:
parte de lo que el repositorio muestra es cómo se construye, no solo qué hace.

## Ramas

Se usa **GitHub Flow**: `main` siempre desplegable, y una rama por cambio.

```
feat/autenticacion-jwt      una funcionalidad nueva
fix/lazy-init-comprobante   corrección de un defecto
docs/guia-de-deploy         solo documentación
chore/bump-spring-boot      mantenimiento, dependencias, tooling
```

No se commitea directo a `main`. Cada rama entra por pull request, aunque el que la
revise sea el mismo que la escribió: el PR es donde queda escrito *por qué* se hizo el
cambio, y eso sobrevive mucho más que el recuerdo.

Se descartó git-flow (con `develop` y ramas de release) por lo mismo que se descartaron
Kubernetes y el service discovery: sin un equipo ni releases paralelas que sostener, es
ceremonia sin problema que resolver.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/): el asunto
declara el tipo y el alcance, y el cuerpo explica el porqué.

```
feat(venta): agregar venta atomica con idempotencia
fix: habilitar el CI en Linux y estabilizar la serializacion de paginas
docs(readme): reorientar el README a guia de uso
```

El cuerpo importa más que el asunto. Un commit que dice *qué* cambió es redundante con
el diff; uno que dice *por qué* es lo único que no se puede reconstruir después.

## Versionado

[SemVer](https://semver.org/lang/es/), con la versión en el `pom.xml` del módulo raíz.

| Cambio | Bump |
|---|---|
| Se rompe el contrato de la API | `MAJOR` |
| Funcionalidad nueva compatible | `MINOR` |
| Corrección sin cambio de contrato | `PATCH` |

Mientras el proyecto esté en `0.x`, se admite romper el contrato en un `MINOR`, que es
lo que SemVer contempla para versiones iniciales.

## Publicar una versión

1. Que `main` esté en verde.
2. Bump de `<version>` en el `pom.xml` raíz, sin el sufijo `-SNAPSHOT`.
3. Mover lo que esté en *No publicado* del `CHANGELOG.md` a la versión nueva, con fecha.
4. Commit `chore(release): v0.2.0`, tag anotado y push del tag.
5. Publicar la release en GitHub tomando el texto del changelog.
6. Volver a `-SNAPSHOT` con el siguiente número.

```bash
git tag -a v0.2.0 -m "v0.2.0 — autenticación con JWT"
git push origin v0.2.0
```

El tag es anotado y no liviano a propósito: guarda autor, fecha y mensaje, y es el único
que `git describe` considera por defecto.

## Antes de abrir un PR

```bash
./mvnw verify
```

Tiene que pasar entero, con los tests de integración incluidos. Requieren Docker porque
levantan PostgreSQL y Kafka reales: son los que detectan los problemas de concurrencia,
que es justamente lo que este proyecto se propone demostrar.
