"""生成「麓风聊天」桌面端应用图标（icon.png / icon.ico）。

设计：品牌绿渐变圆角方块 + 居中白色「麓」字，与 Web 端 logo 保持一致。
用法：python gen-icon.py   （需 Pillow）
"""

import os

from PIL import Image, ImageDraw, ImageFont

SIZE = 512
SS = 4  # 超采样倍数，抗锯齿
W = SIZE * SS

# 品牌绿：顶部亮一点 → 底部深一点
COLOR_TOP = (16, 209, 122)    # #10d17a
COLOR_BOTTOM = (6, 173, 86)   # #06ad56

here = os.path.dirname(os.path.abspath(__file__))

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))

# 1) 垂直渐变
gradient = Image.new("RGB", (W, W))
gd = ImageDraw.Draw(gradient)
for y in range(W):
    t = y / (W - 1)
    r = int(COLOR_TOP[0] + (COLOR_BOTTOM[0] - COLOR_TOP[0]) * t)
    g = int(COLOR_TOP[1] + (COLOR_BOTTOM[1] - COLOR_TOP[1]) * t)
    b = int(COLOR_TOP[2] + (COLOR_BOTTOM[2] - COLOR_TOP[2]) * t)
    gd.line([(0, y), (W, y)], fill=(r, g, b))

# 2) 圆角遮罩
mask = Image.new("L", (W, W), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([0, 0, W - 1, W - 1], radius=int(W * 0.22), fill=255)
img.paste(gradient, (0, 0), mask)

# 3) 居中白字「麓」
draw = ImageDraw.Draw(img)
font_path = "C:/Windows/Fonts/msyhbd.ttc"  # 微软雅黑 Bold
if not os.path.exists(font_path):
    font_path = "C:/Windows/Fonts/msyh.ttc"
font = ImageFont.truetype(font_path, int(W * 0.60))
text = "麓"
bbox = draw.textbbox((0, 0), text, font=font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
x = (W - tw) / 2 - bbox[0]
y = (W - th) / 2 - bbox[1]
draw.text((x, y), text, font=font, fill=(255, 255, 255, 255))

# 4) 降采样并导出
icon = img.resize((SIZE, SIZE), Image.LANCZOS)
icon.save(os.path.join(here, "icon.png"))
icon.save(
    os.path.join(here, "icon.ico"),
    sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)],
)
print("icon.png / icon.ico generated in", here)
