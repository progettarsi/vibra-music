"""Draws the README artwork into .github/readme/.

    pip install pillow fonttools uharfbuzz
    python scripts/readme/build.py

Every file is rebuilt from scratch. To change the screens in the showcase, replace the
pictures in scripts/readme/shots/ (portrait phone screenshots) and run it again.
"""
import os
import gfx

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(gfx.REPO, '.github', 'readme')
os.makedirs(OUT, exist_ok=True)
for old in os.listdir(OUT):
    if old.endswith('.svg'):
        os.remove(os.path.join(OUT, old))


def write(name, content):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8', newline='') as f:
        f.write(content)


write('hero.svg', gfx.hero())
write('button-download.svg', gfx.button('Download for Android', 'primary'))
write('button-releases.svg', gfx.button('All releases', 'secondary'))
write('shape-spring.svg', gfx.principle('puffy', 'lavender', 'breathe'))
write('shape-place.svg', gfx.principle('cookie9', 'pink', 'spin'))
write('shape-touch.svg', gfx.principle('cookie12', 'warm', 'press'))
write('feature-flow.svg', gfx.feature_flow())
write('feature-player.svg', gfx.feature_player())
write('feature-lyrics.svg', gfx.feature_lyrics())
write('feature-blocks.svg', gfx.feature_blocks())
write('feature-shapes.svg', gfx.feature_shapes())
write('feature-engine.svg', gfx.feature_engine())
# The README is one song: each section moves the seek bar further.
for k in range(1, 8):
    for theme in ('dark', 'light'):
        write(f'track-{k}-{theme}.svg', gfx.track(k / 8, theme))
write('signoff.svg', gfx.signoff())
shots = os.path.join(HERE, 'shots')
write('showcase.svg', gfx.showcase([
    (gfx.jpeg_data(os.path.join(shots, 'home.jpg')), 'Home'),
    (gfx.jpeg_data(os.path.join(shots, 'player.jpg')), 'Player'),
    (gfx.jpeg_data(os.path.join(shots, 'profile.jpg')), 'Profile'),
    (gfx.jpeg_data(os.path.join(shots, 'library.jpg')), 'Library'),
]))

files = sorted(os.listdir(OUT))
total = sum(os.path.getsize(os.path.join(OUT, f)) for f in files)
print(f'{len(files)} files in .github/readme/, {total // 1024} KB')
