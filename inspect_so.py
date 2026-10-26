import re

so_path = r'C:\Users\Ritu Chaudhary\.gemini\antigravity\scratch\UniversalVirtualCore\core\src\main\jniLibs\arm64-v8a\libalok.so'
with open(so_path, 'rb') as f:
    data = f.read()

print("File size:", len(data))
print("Contains addIORule:", b"addIORule" in data)

strings = re.findall(b'[\x20-\x7E]{4,}', data)
for s in strings:
    s_str = s.decode('ascii', errors='ignore')
    if any(k in s_str.lower() for k in ['iorule', 'redirect', 'relocate', 'whitelist', 'black', 'sdcard', 'emulated', 'obb']):
        print(s_str)
