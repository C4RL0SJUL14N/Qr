import json
import queue
import socket
import threading
import time
import tkinter as tk
from datetime import datetime
from tkinter import ttk, messagebox


class WifiReceiverApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("Servidor WiFi - Recepcion de datos")
        self.root.geometry("980x620")

        self.server_socket = None
        self.server_thread = None
        self.accepting = False
        self.clients = {}
        self.clients_lock = threading.Lock()
        self.events = queue.Queue()

        self.host_var = tk.StringVar(value="0.0.0.0")
        self.port_var = tk.StringVar(value="5050")
        self.status_var = tk.StringVar(value="Detenido")
        self.local_ip_var = tk.StringVar(value=self._get_local_ip())

        self._build_ui()
        self._schedule_ui_pump()

    def _build_ui(self) -> None:
        main = ttk.Frame(self.root, padding=12)
        main.pack(fill="both", expand=True)

        config = ttk.LabelFrame(main, text="Configuracion del servidor", padding=10)
        config.pack(fill="x")

        ttk.Label(config, text="Host:").grid(row=0, column=0, sticky="w", padx=(0, 6), pady=4)
        ttk.Entry(config, textvariable=self.host_var, width=18).grid(row=0, column=1, sticky="w", pady=4)

        ttk.Label(config, text="Puerto:").grid(row=0, column=2, sticky="w", padx=(18, 6), pady=4)
        ttk.Entry(config, textvariable=self.port_var, width=10).grid(row=0, column=3, sticky="w", pady=4)

        ttk.Label(config, text="IP local:").grid(row=0, column=4, sticky="w", padx=(18, 6), pady=4)
        ttk.Label(config, textvariable=self.local_ip_var).grid(row=0, column=5, sticky="w", pady=4)

        self.start_btn = ttk.Button(config, text="Iniciar conexion", command=self.start_server)
        self.start_btn.grid(row=1, column=0, columnspan=2, sticky="we", pady=(10, 4), padx=(0, 8))

        self.stop_btn = ttk.Button(config, text="Detener conexion", command=self.stop_server, state="disabled")
        self.stop_btn.grid(row=1, column=2, columnspan=2, sticky="we", pady=(10, 4), padx=(0, 8))

        ttk.Label(config, text="Estado:").grid(row=1, column=4, sticky="e", padx=(12, 6))
        ttk.Label(config, textvariable=self.status_var).grid(row=1, column=5, sticky="w")

        config.columnconfigure(5, weight=1)

        body = ttk.Panedwindow(main, orient="horizontal")
        body.pack(fill="both", expand=True, pady=(12, 0))

        clients_frame = ttk.LabelFrame(body, text="Equipos conectados", padding=8)
        body.add(clients_frame, weight=1)

        self.clients_list = tk.Listbox(clients_frame, height=18)
        self.clients_list.pack(fill="both", expand=True)

        logs_frame = ttk.LabelFrame(body, text="Registro de informacion recibida", padding=8)
        body.add(logs_frame, weight=3)

        self.log_text = tk.Text(logs_frame, wrap="word", state="disabled")
        self.log_text.pack(fill="both", expand=True, side="left")

        scrollbar = ttk.Scrollbar(logs_frame, orient="vertical", command=self.log_text.yview)
        scrollbar.pack(fill="y", side="right")
        self.log_text.configure(yscrollcommand=scrollbar.set)

    def _get_local_ip(self) -> str:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
        except OSError:
            return "No detectada"
        finally:
            sock.close()

    def start_server(self) -> None:
        if self.accepting:
            return

        try:
            port = int(self.port_var.get().strip())
            if not (1 <= port <= 65535):
                raise ValueError
        except ValueError:
            messagebox.showerror("Puerto invalido", "Ingresa un puerto entre 1 y 65535.")
            return

        host = self.host_var.get().strip() or "0.0.0.0"

        try:
            server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind((host, port))
            server.listen(20)
            server.settimeout(1.0)
        except OSError as exc:
            messagebox.showerror("Error al iniciar", f"No se pudo iniciar el servidor:\n{exc}")
            return

        self.server_socket = server
        self.accepting = True
        self.start_btn.configure(state="disabled")
        self.stop_btn.configure(state="normal")
        self.status_var.set(f"Escuchando en {host}:{port}")
        self._log(f"Servidor iniciado en {host}:{port} (IP local: {self.local_ip_var.get()})")

        self.server_thread = threading.Thread(target=self._accept_loop, daemon=True)
        self.server_thread.start()

    def stop_server(self) -> None:
        if not self.accepting:
            return

        self.accepting = False
        if self.server_socket is not None:
            try:
                self.server_socket.close()
            except OSError:
                pass
            self.server_socket = None

        with self.clients_lock:
            for client_sock, address in list(self.clients.items()):
                try:
                    client_sock.close()
                except OSError:
                    pass
                self.events.put(("client_disconnected", address))
            self.clients.clear()

        self.start_btn.configure(state="normal")
        self.stop_btn.configure(state="disabled")
        self.status_var.set("Detenido")
        self._log("Servidor detenido")

    def _accept_loop(self) -> None:
        while self.accepting and self.server_socket is not None:
            try:
                client_sock, address = self.server_socket.accept()
                client_sock.settimeout(1.0)
            except socket.timeout:
                continue
            except OSError:
                break

            with self.clients_lock:
                self.clients[client_sock] = address
            self.events.put(("client_connected", address))

            threading.Thread(
                target=self._client_loop,
                args=(client_sock, address),
                daemon=True,
            ).start()

    def _client_loop(self, client_sock: socket.socket, address) -> None:
        buffer = ""
        while self.accepting:
            try:
                data = client_sock.recv(4096)
            except socket.timeout:
                continue
            except OSError:
                break

            if not data:
                break

            buffer += data.decode("utf-8", errors="replace")
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                parsed = self._normalize_payload(line)
                self.events.put(("log", address, parsed))

        with self.clients_lock:
            self.clients.pop(client_sock, None)
        try:
            client_sock.close()
        except OSError:
            pass
        self.events.put(("client_disconnected", address))

    def _normalize_payload(self, payload: str) -> str:
        try:
            obj = json.loads(payload)
            if isinstance(obj, dict):
                ordered = ", ".join(f"{k}={v}" for k, v in obj.items())
                return f"JSON: {ordered}"
            return f"JSON: {obj}"
        except json.JSONDecodeError:
            return payload

    def _schedule_ui_pump(self) -> None:
        self._drain_events()
        self.root.after(200, self._schedule_ui_pump)

    def _drain_events(self) -> None:
        while True:
            try:
                event = self.events.get_nowait()
            except queue.Empty:
                return

            kind = event[0]
            if kind == "client_connected":
                address = event[1]
                self._refresh_clients()
                self._log(f"Cliente conectado: {address[0]}:{address[1]}")
            elif kind == "client_disconnected":
                address = event[1]
                self._refresh_clients()
                self._log(f"Cliente desconectado: {address[0]}:{address[1]}")
            elif kind == "log":
                address, payload = event[1], event[2]
                self._log(f"{address[0]}:{address[1]} -> {payload}")

    def _refresh_clients(self) -> None:
        with self.clients_lock:
            items = [f"{addr[0]}:{addr[1]}" for addr in self.clients.values()]
        items.sort()

        self.clients_list.delete(0, tk.END)
        if not items:
            self.clients_list.insert(tk.END, "(Sin equipos conectados)")
            return
        for item in items:
            self.clients_list.insert(tk.END, item)

    def _log(self, message: str) -> None:
        stamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        row = f"[{stamp}] {message}\n"

        self.log_text.configure(state="normal")
        self.log_text.insert("end", row)
        self.log_text.see("end")
        self.log_text.configure(state="disabled")


def main() -> None:
    root = tk.Tk()
    app = WifiReceiverApp(root)

    def on_close() -> None:
        app.stop_server()
        # Breve espera para liberar sockets en Windows.
        time.sleep(0.1)
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()


if __name__ == "__main__":
    main()
