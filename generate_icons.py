#!/usr/bin/env python3
"""
Generate launcher icons for CreoleTranslator Android app.
Requires Pillow: pip3 install Pillow
"""
import os
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("Install Pillow first: pip3 install Pillow")
    exit(1)

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def create_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Purple gradient background (filled circle)
    for y in range(size):
        ratio = y / size
        r = int(123 * (1 - ratio) + 233 * ratio)
        g = int(45 * (1 - ratio) + 30 * ratio)
        b = int(139 * (1 - ratio) + 140 * ratio)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # Mask to circle
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([0, 0, size, size], fill=255)
    img.putalpha(mask)

    # Add mic emoji text
    mic_size = int(size * 0.55)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Apple Color Emoji.ttc", mic_size)
    except Exception:
        font = ImageFont.load_default()

    draw = ImageDraw.Draw(img)
    try:
        bbox = draw.textbbox((0, 0), "\U0001F3A4", font=font)
        text_w = bbox[2] - bbox[0]
        text_h = bbox[3] - bbox[1]
        x = (size - text_w) // 2 - bbox[0]
        y = (size - text_h) // 2 - bbox[1]
        draw.text((x, y), "\U0001F3A4", font=font, embedded_color=True)
    except Exception:
        pass

    return img

res_dir = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res")

for folder, size in SIZES.items():
    icon = create_icon(size)
    out_path = os.path.join(res_dir, folder, "ic_launcher.png")
    icon.save(out_path)
    round_out_path = os.path.join(res_dir, folder, "ic_launcher_round.png")
    icon.save(round_out_path)
    print(f"Generated {folder}/ic_launcher.png ({size}x{size})")

print("\nIcons generated successfully!")
