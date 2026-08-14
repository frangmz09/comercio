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

## Merge

Se usa **merge commit** (`--no-ff`), no squash ni rebase.

El grafo conserva qué commits vinieron juntos y de qué rama salieron. Esa agrupación es
información: dice que la corrección del CI y el cambio de documentación formaron parte de
entregar la autenticación, y no fueron tres cosas sueltas que cayeron el mismo día.

El mensaje del merge queda como lo propone GitHub:

```
Merge pull request #1 from frangmz09/feat/autenticacion-jwt

feat(auth): agregar autenticacion con JWT y roles
```

No lleva formato Conventional Commits, y está bien: la especificación no cubre los
merges, y las herramientas que validan el formato los ignoran por defecto. Ponerle
`chore(merge):` sería inventar una convención que nadie sigue.

Las otras dos estrategias tienen su lugar:

- **Squash** cuando la rama trae commits de trabajo en progreso —«wip», «arreglo typo»,
  «ahora sí»— que no le sirven a nadie en el historial. Si los commits están escritos
  para ser leídos, aplastarlos tira ese trabajo.
- **Rebase** cuando se busca una historia perfectamente lineal. Se descarta acá porque
  pierde la agrupación por rama, que es justamente lo que hace legible el grafo.

La rama se borra después del merge: ya quedó registrada en el commit de merge y en el PR.

## Versionado

[SemVer](https://semver.org/lang/es/), con la versión en el `pom.xml` del módulo raíz.

| Cambio | Bump |
|---|---|
| Se rompe el contrato de la API | `MAJOR` |
| Funcionalidad nueva compatible | `MINOR` |
| Corrección sin cambio de contrato | `PATCH` |

Mientras el proyecto esté en `0.x`, se admite romper el contrato en un `MINOR`, que es
lo que SemVer contempla para versiones iniciales: en la versión mayor cero la API pública
no se considera estable.

La pregunta que decide el bump no es cuánto código cambió, sino **qué se rompe para quien
consume la API**. Un refactor de tres mil líneas que no toca el contrato es `PATCH`;
agregar una validación obligatoria a un campo existente son dos líneas y rompe el
contrato. Un `PATCH` le está diciendo a quien integra que puede actualizar sin mirar, así
que usarlo para un cambio incompatible es peor que equivocarse de número: es dar una
garantía falsa.

Todo cambio incompatible se marca de forma visible en el `CHANGELOG.md`, con qué se rompe
y qué hay que hacer para adaptarse.

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

## Análisis de código

SonarCloud analiza el proyecto desde el CI, con el scanner de Maven. Es importante que
sea así y no con el **Análisis Automático** de SonarCloud: ese modo escanea el
repositorio sin ejecutar el build, y entonces no puede leer el reporte de cobertura de
JaCoCo ni la configuración del `pom.xml`. Además, mientras está activo, SonarCloud
rechaza el análisis que envía el CI.

Se desactiva en **Administration → Analysis Method**.

El paso de análisis no frena el merge: que el proyecto compile y los tests pasen es una
afirmación sobre el código, mientras que la disponibilidad de un servicio externo no lo
es. Pero si falla, el run deja un aviso visible en su resumen — un `continue-on-error`
silencioso puede tener el análisis roto durante semanas sin que nadie se entere.

## Antes de abrir un PR

```bash
./mvnw verify
```

Tiene que pasar entero, con los tests de integración incluidos. Requieren Docker porque
levantan PostgreSQL y Kafka reales: son los que detectan los problemas de concurrencia,
que es justamente lo que este proyecto se propone demostrar.
