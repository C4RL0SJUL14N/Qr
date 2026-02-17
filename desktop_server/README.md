# Servidor de escritorio WiFi (Python)

Esta aplicacion recibe informacion desde equipos Android conectados a la misma red WiFi.

## Requisitos

- Python 3.10+
- Tkinter (incluido normalmente en Python para Windows)

## Ejecutar

```powershell
cd desktop_server
python server_gui.py
```

## Como conectar desde Android

1. Conecta el celular y el PC a la misma red WiFi.
2. Abre la app de escritorio y pulsa `Iniciar conexion`.
3. Usa en Android la IP local mostrada y el puerto (por defecto `5050`).
4. Envia mensajes por TCP como texto UTF-8 terminado en salto de linea (`\n`).

Ejemplos de payload soportado:

```txt
identificacion:12345;nombres:Ana;grado:10A
```

```json
{"identificacion":"12345","nombres":"Ana","grado":"10A"}
```

## Funcionalidades

- Visualiza equipos conectados en tiempo real.
- Boton para iniciar/detener conexion de equipos.
- Registro en vivo de la informacion recibida.
