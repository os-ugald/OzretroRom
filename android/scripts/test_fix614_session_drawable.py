#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
drawables = root / 'app' / 'src' / 'main' / 'res' / 'drawable'
session_bg = drawables / 'bg_session_console.xml'
parsed = ET.parse(session_bg).getroot()
tag = parsed.tag.split('}')[-1]
assert tag == 'shape', f'bg_session_console root={tag}, expected shape'
gradients = [child for child in parsed if child.tag.split('}')[-1] == 'gradient']
assert gradients, 'bg_session_console must contain a gradient child'
for xml in drawables.glob('*.xml'):
    tag = ET.parse(xml).getroot().tag.split('}')[-1]
    assert tag != 'gradient', f'{xml.name}: gradient cannot be a drawable root'
print('FIX614_SESSION_DRAWABLE_OK')
