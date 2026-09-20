"""一键启动记账本网页版：监听局域网，手机可通过 Wi-Fi 打开。

用法：双击上级目录的 start-moneybook.bat，或直接
      python tools/start_server.py
"""
import http.server
import socket
import socketserver
import sys
import threading
import webbrowser
from pathlib import Path

PORT = 8765
WEB_DIR = Path(__file__).resolve().parent.parent / "web"


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class Handler(http.server.SimpleHTTPRequestHandler):
    """以 web/ 为根目录，并禁止缓存 index.html，保证改动立刻生效。"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

    def log_message(self, fmt, *args):
        pass  # 静默，控制台只保留我们的提示


def main() -> int:
    if not (WEB_DIR / "index.html").exists():
        print(f"[错误] 找不到网页文件：{WEB_DIR / 'index.html'}")
        return 1

    ip = lan_ip()
    line = "=" * 46
    print(line)
    print("   记账本 MoneyBook 已启动")
    print(line)
    print(f"   本机打开：   http://127.0.0.1:{PORT}")
    print(f"   手机打开：   http://{ip}:{PORT}")
    print()
    print("   手机需与电脑连同一个 Wi-Fi；首次运行若弹出")
    print("   Windows 防火墙提示，请勾选“专用网络”并允许访问。")
    print("   停止服务：关闭本窗口（记账数据存在浏览器里，不受影响）。")
    print(line)
    print("   注意：局域网是 http，浏览器不会启用离线缓存，")
    print("   也不能“添加到主屏幕”；要离线用请发布到 https 链接。")
    print(line)
    sys.stdout.flush()

    threading.Timer(0.6, lambda: webbrowser.open(f"http://127.0.0.1:{PORT}")).start()

    socketserver.TCPServer.allow_reuse_address = True
    try:
        with socketserver.ThreadingTCPServer(("0.0.0.0", PORT), Handler) as httpd:
            httpd.serve_forever()
    except OSError as e:
        print(f"[错误] 端口 {PORT} 被占用或无法监听：{e}")
        print("       请关掉其它正在运行的记账本窗口后重试。")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
