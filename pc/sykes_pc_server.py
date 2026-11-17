"""
SykesLink PC Server © 2026 Dominic Sykes
Permanent License Granted.
"""
import socket
import threading
import os
import json
import customtkinter as ctk
from tkinter import filedialog, messagebox

ctk.set_appearance_mode("System")
ctk.set_default_color_theme("blue")

PORT = 50555
DISCOVERY_PORT = 50556

class SykesPCApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("SykesLink PC Bridge © 2026 Dominic Sykes")
        self.geometry("750x550")
        self.minsize(700, 500)

        self.downloads_folder = os.path.join(os.path.expanduser("~"), "Downloads", "SykesLink")
        os.makedirs(self.downloads_folder, exist_ok=True)

        self.local_ip = self.get_local_ip()

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(2, weight=1)

        self.header_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.header_frame.grid(row=0, column=0, padx=20, pady=(20, 10), sticky="ew")
        
        self.title_label = ctk.CTkLabel(self.header_frame, text="SykesLink PC Transfer Hub", font=ctk.CTkFont(size=22, weight="bold"))
        self.title_label.pack(side="left")

        self.ip_label = ctk.CTkLabel(self.header_frame, text=f"Host IP: {self.local_ip} | Port: {PORT}", font=ctk.CTkFont(size=12))
        self.ip_label.pack(side="right", padx=5)

        self.control_frame = ctk.CTkFrame(self)
        self.control_frame.grid(row=1, column=0, padx=20, pady=10, sticky="ew")
        
        self.send_btn = ctk.CTkButton(self.control_frame, text="Select & Send File to Android", command=self.select_file_to_send)
        self.send_btn.pack(side="left", padx=15, pady=15)

        self.folder_btn = ctk.CTkButton(self.control_frame, text="Open Received Folder", command=self.open_downloads, fg_color="gray")
        self.folder_btn.pack(side="right", padx=15, pady=15)

        self.log_frame = ctk.CTkFrame(self)
        self.log_frame.grid(row=2, column=0, padx=20, pady=(10, 20), sticky="nsew")
        self.log_frame.grid_columnconfigure(0, weight=1)
        self.log_frame.grid_rowconfigure(1, weight=1)

        self.log_title = ctk.CTkLabel(self.log_frame, text="Live Transfer & Status Logs", font=ctk.CTkFont(size=14, weight="bold"))
        self.log_title.grid(row=0, column=0, sticky="w", padx=15, pady=(10, 0))

        self.log_box = ctk.CTkTextbox(self.log_frame, state="disabled", font=("Consolas", 11))
        self.log_box.grid(row=1, column=0, padx=15, pady=15, sticky="nsew")

        self.log(f"Initialized SykesLink Server. Saving received items to: {self.downloads_folder}")
        self.start_threads()

    def get_local_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
        except Exception:
            ip = "127.0.0.1"
        finally:
            s.close()
        return ip

    def log(self, message):
        self.log_box.configure(state="normal")
        self.log_box.insert("end", f"[SYS] {message}\n")
        self.log_box.see("end")
        self.log_box.configure(state="disabled")

    def start_threads(self):
        threading.Thread(target=self.tcp_receiver_server, daemon=True).start()
        threading.Thread(target=self.udp_broadcast_responder, daemon=True).start()

    def udp_broadcast_responder(self):
        udps = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        udps.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            udps.bind(('', DISCOVERY_PORT))
            while True:
                data, addr = udps.recvfrom(1024)
                if data == b"SYKESLINK_DISCOVER_PC":
                    udps.sendto(f"SYKESLINK_HERE:{self.local_ip}".encode('utf-8'), addr)
        except Exception as e:
            self.log(f"UDP Discovery Warning: {e}")

    def tcp_receiver_server(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(('0.0.0.0', PORT))
        server.listen(5)
        self.log(f"TCP Server listening on port {PORT}...")

        while True:
            client_sock, addr = server.accept()
            threading.Thread(target=self.handle_incoming_transfer, args=(client_sock, addr), daemon=True).start()

    def handle_incoming_transfer(self, sock, addr):
        try:
            raw_len = sock.recv(4)
            if not raw_len:
                return
            header_len = int.from_bytes(raw_len, 'big')
            header_data = json.loads(sock.recv(header_len).decode('utf-8'))

            filename = header_data['filename']
            filesize = header_data['filesize']

            filepath = os.path.join(self.downloads_folder, filename)
            self.log(f"Receiving '{filename}' ({filesize} bytes) from {addr[0]}...")

            received = 0
            with open(filepath, 'wb') as f:
                while received < filesize:
                    chunk = sock.recv(min(filesize - received, 65536))
                    if not chunk:
                        break
                    f.write(chunk)
                    received += len(chunk)

            self.log(f"Successfully received: {filename}")
            sock.sendall(b"ACK_SUCCESS")
        except Exception as e:
            self.log(f"Transfer error from {addr[0]}: {e}")
        finally:
            sock.close()

    def select_file_to_send(self):
        filepath = filedialog.askopenfilename(title="Select file to send to Android")
        if not filepath:
            return
        
        target_ip = ctk.CTkInputDialog(text="Enter Android Device IP Address:", title="Connect to Android").get_input()
        if not target_ip:
            return

        threading.Thread(target=self.send_file_to_android_thread, args=(filepath, target_ip), daemon=True).start()

    def send_file_to_android_thread(self, filepath, ip):
        try:
            filename = os.path.basename(filepath)
            filesize = os.path.getsize(filepath)
            self.log(f"Connecting to Android at {ip}:{PORT} to send {filename}...")

            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect((ip, PORT))

            header = json.dumps({"filename": filename, "filesize": filesize}).encode('utf-8')
            s.sendall(len(header).to_bytes(4, 'big') + header)

            with open(filepath, 'rb') as f:
                while True:
                    chunk = f.read(65536)
                    if not chunk:
                        break
                    s.sendall(chunk)

            ack = s.recv(16)
            if ack == b"ACK_SUCCESS":
                self.log(f"File successfully sent to Android: {filename}")
            else:
                self.log(f"File sent, but no confirmation ACK received.")
            s.close()
        except Exception as e:
            self.log(f"Failed to send file to Android: {e}")
            messagebox.showerror("Transfer Error", str(e))

    def open_downloads(self):
        os.startfile(self.downloads_folder)

if __name__ == "__main__":
    app = SykesPCApp()
    app.mainloop()
