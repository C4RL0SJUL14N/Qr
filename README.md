# QR Estudiantes (Android)

App Android en Kotlin + Jetpack Compose para leer un QR con datos de estudiante y mostrar:

- Identificacion
- Nombres
- Apellidos
- Grado
- Fecha y hora de lectura (se agrega automaticamente)
- Observacion (opcional)

## Formatos de QR soportados

1. JSON:

```json
{
  "identificacion": "12345",
  "nombres": "Ana Maria",
  "apellidos": "Lopez Perez",
  "grado": "10A"
}
```

2. Texto clave-valor (separado por salto de linea, `;` o `|`):

```txt
identificacion:12345
nombres:Ana Maria
apellidos:Lopez Perez
grado:10A
```

## Abrir el proyecto

1. Abre esta carpeta en Android Studio.
2. Deja que Android Studio sincronice Gradle.
3. Ejecuta en un dispositivo Android con camara.
