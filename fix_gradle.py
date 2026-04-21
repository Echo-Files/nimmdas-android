import re

with open('/tmp/nimmdas-android/app/build.gradle', 'r') as f:
    content = f.read()

replacements = {
    "themeColorDark: '#000000'": "themeColorDark: '#16A34A'",
    "navigationColor: '#000000'": "navigationColor: '#FFFFFF'",
    "navigationColorDark: '#000000'": "navigationColorDark: '#16A34A'",
    "navigationDividerColor: '#000000'": "navigationDividerColor: '#FFFFFF'",
    "navigationDividerColorDark: '#000000'": "navigationDividerColorDark: '#16A34A'",
}

for old, new in replacements.items():
    content = content.replace(old, new)

with open('/tmp/nimmdas-android/app/build.gradle', 'w') as f:
    f.write(content)

print('build.gradle colors updated')
