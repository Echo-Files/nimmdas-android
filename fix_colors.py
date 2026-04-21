import json

with open('/tmp/nimmdas-android/twa-manifest.json') as f:
    d = json.load(f)

d['themeColor'] = '#22C55E'
d['themeColorDark'] = '#16A34A'
d['navigationColor'] = '#FFFFFF'
d['navigationColorDark'] = '#16A34A'
d['navigationDividerColor'] = '#FFFFFF'
d['navigationDividerColorDark'] = '#16A34A'
d['backgroundColor'] = '#FFFFFF'

with open('/tmp/nimmdas-android/twa-manifest.json', 'w') as f:
    json.dump(d, f, indent=2)

print('Colors updated successfully')
