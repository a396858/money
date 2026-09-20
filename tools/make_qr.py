"""生成「手机扫码打开记账本」二维码 —— 自动探测本机局域网 IP"""
import socket
import os
import qrcode

PORT = 8765
OUT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


ip = lan_ip()
url = f"http://{ip}:{PORT}"
print("局域网地址:", url)

qr = qrcode.QRCode(
    version=None,
    error_correction=qrcode.constants.ERROR_CORRECT_M,
    box_size=10,
    border=2,
)
qr.add_data(url)
qr.make(fit=True)
img = qr.make_image(fill_color="#1f2937", back_color="white").convert("RGB")

out = os.path.join(OUT_DIR, "手机访问二维码.png")
img.save(out)
print("已生成:", out, img.size)
