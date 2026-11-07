# Motor de conversión dual-fisheye → 360° (fase 1)

## Objetivo verificable
Convertir el video MP4 original de LG R105 (dos círculos ojo de pez lado a lado) en un video equirectangular 2:1 continuo, con audio, metadatos esféricos y calibración reproducible. **No** basta con renombrar, girar o difuminar.

## Prototipo de referencia en escritorio (NO integrado aún en Android)
Requiere FFmpeg compilado con el filtro `v360` y FFprobe. Comprobar:
```bash
ffmpeg -filters | grep v360
ffprobe -v error -show_streams -of json entrada.mp4
```
Primer ensayo geométrico (parámetros provisionales, no calibración validada):
```bash
ffmpeg -i entrada.mp4 -vf "v360=input=dfisheye:output=equirect:ih_fov=190:iv_fov=190:w=2560:h=1280" -map 0:v:0 -map 0:a? -c:v libx264 -crf 19 -preset medium -c:a copy salida_prueba.mp4
```
**Advertencia:** el filtro estándar no resuelve por sí solo centros/radios desplazados, orientación, paralaje ni las costuras visibles en la muestra. Esta salida es una línea base, no un resultado listo para publicar.

## Calibración necesaria antes de procesar video
1. Extraer 5-10 fotogramas representativos del MP4 sin reescalar.
2. Estimar por lente centro (cx,cy), radio útil, FOV real, orientación (yaw/pitch/roll) y máscara circular.
3. Crear mapas inversos equirectangulares (remap OpenCV) y validar correspondencias en regiones solapadas.
4. Elegir costura fija/temporalmente estable; corregir exposición y aplicar mezcla multibanda solo en solapamiento.
5. Evaluar con líneas rectas, objetos cercanos, puntos de control y consistencia entre fotogramas. El paralaje cercano no siempre se puede eliminar.
6. Guardar parámetros en perfil versionado (JSON), sin inventar valores ópticos.

## Integración Android posterior
- Motor nativo C++/OpenCV o shader GPU para remapeo por fotograma.
- MediaCodec/MediaExtractor/MediaMuxer para decodificar, codificar y preservar audio, o FFmpeg bajo licencia revisada.
- Procesamiento fuera del hilo UI, progreso, cancelación, límites térmicos y de memoria.
- Exportación MP4 2:1; inyección y verificación de metadatos Spatial Media V2 (esférico).
- Pruebas de calidad, continuidad de costuras, reproducción 360° y compatibilidad YouTube.

## Criterios de aceptación
- Sin líneas de corte abruptas en escenas de prueba.
- Sin duplicación notable de objetos lejanos ni cambios de costura entre fotogramas.
- Duración, sincronía y audio preservados.
- Reproducción esférica confirmada con metadatos, no solo reproducción plana.
- No habilitar botón «Convertir a 360°» como función final antes de estas pruebas.
