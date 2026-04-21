from PIL import Image
import os

icon = Image.open('/tmp/nimmdas-android/store_icon.png')
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}
base = '/tmp/nimmdas-android/app/src/main/res'
for folder, size in sizes.items():
    path = os.path.join(base, folder, 'ic_launcher.png')
    resized = icon.resize((size, size), Image.LANCZOS)
    resized.save(path)
    print(f'Saved {path} ({size}x{size})')

# Also update splash icon
splash_dir = os.path.join(base, 'drawable')
os.makedirs(splash_dir, exist_ok=True)
splash = icon.resize((512, 512), Image.LANCZOS)
splash.save(os.path.join(splash_dir, 'splash.png'))
print('Saved splash icon')
