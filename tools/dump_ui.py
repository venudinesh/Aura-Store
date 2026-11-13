import re, sys
# A uiautomator dump holds the app's own UTF-8 (`·`, narrow no-break space); cp1252 raises on them.
if hasattr(sys.stdout, 'reconfigure'): sys.stdout.reconfigure(encoding='utf-8', errors='replace')
s=open(sys.argv[1] if len(sys.argv)>1 else 'ui.xml',encoding='utf-8').read()
for n in re.findall(r'<node[^>]*>',s):
    tx=re.search(r'text="([^"]*)"',n); cd=re.search(r'content-desc="([^"]*)"',n)
    b=re.search(r'bounds="(\[[0-9,]+\]\[[0-9,]+\])"',n)
    t=(tx.group(1) if tx else '') or (cd.group(1) if cd else '')
    if t.strip(): print(f'{("CD " if cd and cd.group(1) else "   ")+t[:48]:52} {b.group(1)}')
