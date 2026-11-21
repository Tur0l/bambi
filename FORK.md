# Bambi Táctil

Fork de **[Bambi](https://github.com/grodri99/bambi)** (de grodri99) que añade un **modo táctil** para usarlo en el
móvil, en horizontal. Es el mismo APK para el móvil y para la tele: el modo táctil se activa solo en aparatos con
pantalla táctil que no son una tele. En una tele o caja Android TV la app se comporta exactamente como el original.

## Qué cambia respecto al original

**Solo en el móvil (modo táctil):**

- Pantalla completa, con la interfaz a la escala de una tele 1080p (540 dp de alto), para que todas las pantallas
  quepan en horizontal sin rediseñarlas. Se respeta el hueco de la cámara.
- Gesto de atrás del sistema en todas las pantallas (antes solo llegaba al elemento con el foco del mando y, sin
  foco, cerraba la app).
- Inicio: deslizar hacia arriba en la portada baja a las filas; tirar hacia abajo con las filas arriba del todo
  vuelve a la portada (en la tele es ▼/▲).
- Asistente: el teclado sale al tocar los campos de clave, y «Todo listo» se puede deslizar hasta «Empezar a ver».
- Reproductor: toque = controles; doble toque a los lados = ±10 s, en el centro = pausa; arrastrar en horizontal
  para buscar; barra de progreso táctil; brillo (mitad izquierda) y volumen (mitad derecha) deslizando en vertical;
  pellizco para quitar o poner las bandas negras; botones de volver, ±10 s, pausa y audio/subtítulos; las píldoras de
  «Siguiente episodio» y «Saltar intro» se tocan.
- Audio 5.1/7.1 mezclado a estéreo dentro de la app, con el canal central (diálogos) en los dos lados. Algunos
  móviles (visto en un POCO F8 Ultra) aceptan PCM 5.1 por una salida directa y solo sacan los canales delanteros: se
  oían la música y los efectos, pero no los diálogos.

**En todos los aparatos:**

- Se instala como **Bambi Táctil** (`com.shieldstream.tv.tactil`), al lado del Bambi original si se quiere.
- Las actualizaciones automáticas salen de las Releases de este fork.

## Versiones

`1.0.8.1` = la 1.0.8 de grodri99 + la revisión 1 del fork. Al traer una versión nueva del original se pasa a
`1.0.9.1`, `1.0.9.2`… El `versionCode` es el de upstream ×100 + la revisión (`10801`), así siempre sube.

## Mantenimiento

- `main` = el original + los cambios del fork. Los cambios del fork van en ficheros propios siempre que se puede
  (`ui/touch/TouchUi.kt`, `PlayerTouch.kt`, `StereoDownmix.kt`, `TouchWindow.kt`) o marcados con «Fork táctil» en un
  comentario, para que traer el original dé pocos conflictos:

  ```bash
  git fetch upstream
  git merge upstream/main
  ```

- Compilar la versión publicable: la clave de firma **no está en el repo**; su ruta y contraseñas van en
  `local.properties` (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`), y un
  fichero `.public_build` con `1` garantiza que no se cuela ninguna clave personal en el APK:

  ```bash
  bash ./gradlew test :app:assembleRelease
  ```

  El APK se publica en la Release como **`Bambi.apk`** (es el nombre que busca el actualizador).

La licencia es la del original: GPL-3.0.
