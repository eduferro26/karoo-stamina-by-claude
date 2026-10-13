# Karoo Stamina

Campo de datos para Karoo que aproxima la "stamina" con un modelo público:

- **Actual**: W′bal (Skiba 2015, forma diferencial), con CP y W′ configurables.
- **Potencial**: techo que baja con el trabajo acumulado (kJ/kg). Es un modelo propio, sin validar: calíbralo con tus FIT.
- **Pie**: W′ restante y tiempo hasta el agotamiento a la potencia actual (o "sostenible").

## Compilar en GitHub (recomendado)

1. Crea un repositorio en GitHub y sube el contenido de esta carpeta (rama `main`).
2. Crea un token personal (Settings → Developer settings → Personal access tokens) con el permiso `read:packages`.
3. En el repositorio: Settings → Secrets and variables → Actions → New repository secret, con nombre `GPR_TOKEN` y ese token como valor.
   `karoo-ext` está en GitHub Packages, que siempre exige autenticación. Si no creas el secreto, el workflow prueba con `GITHUB_TOKEN`, pero no está garantizado que sirva para un paquete de otra cuenta.
4. Cada push a `main` lanza el workflow `Build` (también a mano desde la pestaña Actions → Build → Run workflow). Ejecuta los tests del modelo y compila el APK de depuración.
5. Al terminar, en la página de la ejecución, sección Artifacts:
   - `karoo-stamina-debug-apk`: el APK, que se instala con `adb install -r app-debug.apk`.
   - `test-reports`: informes de los tests (se suben aunque falle algo).

El workflow usa Gradle 8.9 instalado por la propia acción, así que el repo no necesita el wrapper. Si más adelante quieres tenerlo, ejecuta `gradle wrapper --gradle-version 8.9` en local y súbelo.

## Compilar en local

1. Añade a `~/.gradle/gradle.properties`: `gpr.user=TU_USUARIO` y `gpr.key=TU_TOKEN` (token con `read:packages`).
2. `gradle :app:testDebugUnitTest` para los tests del modelo (JVM pura).
3. `gradle :app:installDebug` con el Karoo conectado por adb.
4. En el Karoo: perfil de actividad → añadir campo → Stamina.

## Ajustes

La app incluye una pantalla de ajustes (`SettingsActivity`) con CP, W′, peso opcional y los tres parámetros de durabilidad, validados por rango. Muestra una previsualización de cuánto bajaría el potencial tras 2000, 4000 y 6000 kJ con los valores escritos, útil para calibrar. Los cambios se aplican al motor en caliente, incluso durante una actividad.

Valores por defecto: CP 250 W, W′ 15 kJ, 20 kJ/kg sin penalización, 0,4 % por kJ/kg, tope 35 %. **Cámbialos antes de fiarte del resultado.**

Si dejas el peso vacío se usa el del perfil del Karoo.

## Puntos a verificar en la primera compilación

No he podido compilar contra el SDK, así que estos puntos están escritos de memoria o por inferencia de la documentación:

- Firma exacta de `addConsumer` (con y sin parámetros) y `removeConsumer`.
- `UserProfile.weight`: tipo y unidad (aquí se asume kg).
- `ViewConfig.viewSize` (se usa para decidir la versión compacta) y el umbral `COMPACT_HEIGHT_PX`.
- `DataPoint(...)` y `DataType.Field.SINGLE` al emitir el valor numérico.
- Colores de texto con `?android:attr/textColorPrimary`: comprobar que se leen bien en el tema claro y oscuro del Karoo.
- La pantalla de ajustes se declara como actividad de lanzador (MAIN/LAUNCHER). Hay que comprobar que el Karoo la muestra en su listado de apps; si no, habrá que buscar otra vía de acceso.
- FTP: no he confirmado que el perfil lo exponga, por eso la CP es un ajuste propio.

## Pendiente

Escribir el valor en el FIT (`startFit`), alertas por umbral y calibración con tus archivos.
