import socket
import json
import threading
import time

clients = {}

def handle_client(conn, addr):
    client_ip = addr[0]
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            
            message = json.loads(data.decode('utf-8').strip())
            msg_type = message.get("type")
            
            if msg_type == "CLIENT_INFO":
                info = message.get("payload", {})
                hostname = info.get("hostname", "Unknown")
                clients[client_ip] = {
                    "hostname": hostname,
                    "status": "Online",
                    "last_heartbeat": time.time()
                }
                print(f"[SERVER] Nhận thông tin từ {hostname} ({client_ip}) - OS: {info.get('os')}")
            
            elif msg_type == "HEARTBEAT":
                if client_ip in clients:
                    clients[client_ip]["last_heartbeat"] = time.time()
                    clients[client_ip]["status"] = "Online"
                print(f"[SERVER] Nhận HEARTBEAT từ {client_ip}")

    except Exception as e:
        print(f"[SERVER] Lỗi kết nối với {client_ip}: {e}")
    finally:
        if client_ip in clients:
            clients[client_ip]["status"] = "Offline"
            print(f"[SERVER] Máy {clients[client_ip]['hostname']} ({client_ip}) đã Offline.")
        conn.close()

def check_offline_clients():
    while True:
        time.sleep(5)
        current_time = time.time()
        for ip, info in clients.items():
            if info["status"] == "Online" and (current_time - info["last_heartbeat"] > 10):
                info["status"] = "Offline"
                print(f"[SERVER CẢNH BÁO] Không nhận được Heartbeat. Máy {info['hostname']} ({ip}) được xác định là Offline.")

def start_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind(('127.0.0.1', 9999))
    server.listen(5)
    print("=== MOCK SERVER KHỞI ĐỘNG (Port 9999) ===")
    
    # Thread check timeout
    threading.Thread(target=check_offline_clients, daemon=True).start()
    
    try:
        while True:
            conn, addr = server.accept()
            print(f"\n[SERVER] Chấp nhận kết nối từ {addr}")
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("Đóng server.")
        server.close()

if __name__ == '__main__':
    start_server()
