"""Artwork for the vibra README: SVG files drawn from the Vibra design system.

Text is converted to outlines from the app's own Google Sans Flex (with the ROND axis),
so the images look the same on every machine and need no web font.
"""
import io, json, math, os, re, base64
from PIL import Image
import uharfbuzz as hb
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
FONT = os.path.join(REPO, 'app', 'src', 'main', 'res', 'font', 'vibra_google_sans_flex.ttf')

# Design system tokens (dark scheme, plus the few light ones the light-theme files need)
BG = '#141218'; SURF = '#211F26'; SURF_LOW = '#1D1B20'; SURF_HIGH = '#2B2930'
ON = '#E6E0E9'; ON_VAR = '#CAC4D0'; PRIMARY = '#D0BCFF'; ON_PRIMARY = '#381E72'
PRIMARY_CONT = '#4F378B'; ON_PRIMARY_CONT = '#EADDFF'; SEC_CONT = '#4A4458'
OUTLINE = '#938F99'; L_PRIMARY = '#6750A4'; L_ON = '#1D1B20'; L_ON_VAR = '#49454F'

LOGO = ('M14.5807 5.89906C16.3398 -1.70519 27.9206 -2.06954 29.8809 5.45733C30.624 8.29599 31.3543 11.1385 32.0718 13.9846'
        'C32.1511 15.1718 34.1979 15.7578 34.9985 14.4618C35.2972 13.9784 35.5965 13.4954 35.8961 13.0125C38.0611 8.9421 44.6986 '
        '10.2215 45.2773 14.4242C45.3517 15.0243 45.7508 15.5567 46.3851 15.7975C46.9671 16.018 47.6479 15.9475 48.2037 15.528'
        'C48.4296 15.3462 48.6558 15.1645 48.8819 14.983C49.1194 14.7924 49.4202 14.6896 49.7271 14.7071C50.034 14.7244 50.3205 '
        '14.859 50.5255 15.0833C50.7304 15.3075 50.8386 15.6049 50.8283 15.9121C50.8181 16.2194 50.6887 16.5098 50.4776 16.7292'
        'C50.2764 16.9381 50.0752 17.1469 49.8738 17.3556C48.8434 18.4219 47.1656 18.9268 45.6542 18.5767C44.0198 18.2459 42.5424 '
        '16.8886 42.093 15.2035C41.6759 13.8601 39.7313 13.9847 39.4699 14.9999C39.2178 15.5092 38.9653 16.0182 38.7123 16.5271'
        'C36.6436 21.2126 28.6482 20.6694 27.2584 15.3872C26.3347 12.6011 25.4238 9.81129 24.5257 7.01778C24.2036 6.14559 23.4878 '
        '5.7648 22.3998 5.82458C21.3129 5.94628 20.6831 6.51968 20.5278 7.32437C19.5172 11.8061 18.3782 16.2724 16.9857 20.7115'
        'C16.3164 22.8367 15.532 24.9383 14.5833 27.0021C12.9813 30.7447 9.45212 33.6479 5.77169 34.5402C4.23707 34.9341 2.75974 '
        '35.0876 1.12573 34.9512C0.838569 34.9083 0.559737 34.7868 0.34906 34.5798C0.138387 34.3729 0.0133128 34.1012 0.0010038 '
        '33.8158C-0.011297 33.5304 0.0898644 33.249 0.281929 33.0248C0.474002 32.8003 0.741434 32.6553 1.02385 32.5879C2.11591 '
        '32.2404 3.33663 31.6796 4.32908 31.041C6.80526 29.4727 8.08463 28.0542 9.21958 25.3538C10.3922 22.5637 11.0797 20.3397 '
        '11.4436 18.884C12.7089 13.8229 13.469 10.2534 14.5807 5.89906Z')
LOGO_W, LOGO_H = 50.829, 35

# Material icons (24 px grid), the same glyphs the app draws
ICON = {
    'search': 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    'person': 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
    'play': 'M8 5.14v13.72a1 1 0 0 0 1.52.85l10.8-6.86a1 1 0 0 0 0-1.7L9.52 4.29A1 1 0 0 0 8 5.14z',
    'download': 'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
    'arrow': 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
    'prev': 'M6 6h2v12H6zm3.5 6l8.5 6V6z',
    'next': 'M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z',
}


def icon(name, cx, cy, size, fill, extra=''):
    s = size / 24
    return f'<path transform="translate({cx - size / 2:.1f} {cy - size / 2:.1f}) scale({s:.3f})" d="{ICON[name]}" fill="{fill}"{extra}/>'


def n(v):
    s = f'{v:.1f}'
    if s.endswith('.0'):
        s = s[:-2]
    return '0' if s == '-0' else s


# ---------------------------------------------------------------- text as outlines
_face = hb.Face(hb.Blob.from_file_path(FONT))


def _shape(t, wght, rond, tnum):
    font = hb.Font(_face)
    font.set_variations({'wght': wght, 'ROND': rond})
    buf = hb.Buffer()
    buf.add_str(t)
    buf.guess_segment_properties()
    feats = {'kern': True, 'liga': True}
    if tnum:
        feats['tnum'] = True
    hb.shape(font, buf, feats)
    return font, buf


def text_width(t, size, wght=400, rond=0, tnum=False):
    _, buf = _shape(t, wght, rond, tnum)
    return sum(p.x_advance for p in buf.glyph_positions) * size / _face.upem


def text(t, size, x, y, wght=400, rond=0, anchor='start', fill=ON, tnum=False, extra=''):
    font, buf = _shape(t, wght, rond, tnum)
    s = size / _face.upem
    adv = sum(p.x_advance for p in buf.glyph_positions) * s
    ox = {'start': x, 'middle': x - adv / 2, 'end': x - adv}[anchor]
    pen = SVGPathPen(None, ntos=n)
    cx = 0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        tp = TransformPen(pen, (s, 0, 0, -s, ox + (cx + pos.x_offset) * s, y - pos.y_offset * s))
        font.draw_glyph_with_pen(info.codepoint, tp)
        cx += pos.x_advance
    return f'<path d="{pen.getCommands()}" fill="{fill}"{extra}/>'


# ---------------------------------------------------------------- expressive shapes
# The app's own shapes (MaterialShapes and the favourite-artist star), exported once as
# cubic paths in the unit square. Only the ones the artwork uses are kept.
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'shapes.json'), encoding='utf-8') as _f:
    _UNIT = json.load(_f)


def _placed(unit):
    def draw(cx, cy, r):
        out, k = '', 0
        for tok in re.findall(r'[MCZ]|-?\d+(?:\.\d+)?(?:[eE]-?\d+)?', unit):
            if tok in 'MCZ':
                out += tok
                continue
            v = float(tok)
            out += ('' if out[-1] in 'MCZ' else ' ') + n((cx - r + 2 * r * v) if k % 2 == 0 else (cy - r + 2 * r * v))
            k += 1
        return out
    return draw


SHAPE = {name: _placed(d) for name, d in _UNIT.items()}

# Gradients: the mood palettes of the design system plus the release-art tints
GRAD = {
    'warm': ('#E2541B', '#B3261E', '#7D2A6E'),
    'cool': ('#7B2CBF', '#3F2A8C', '#14213D'),
    'lavender': ('#D0BCFF', '#8B6FD6', '#4F378B'),
    'pink': ('#EFB8C8', '#B5637E', '#633B48'),
    'blue': ('#8ECDFF', '#4A86BF', '#18283F'),
}


def lin(id_, name, x1='0', y1='0', x2='1', y2='1'):
    a, b, c = GRAD[name]
    return (f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
            f'<stop offset="0" stop-color="{a}"/><stop offset=".55" stop-color="{b}"/><stop offset="1" stop-color="{c}"/></linearGradient>')


def glow(id_, color, opacity):
    return (f'<radialGradient id="{id_}"><stop offset="0" stop-color="{color}" stop-opacity="{opacity}"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


def wave_path(x0, x1, y, amp, wl):
    d = f'M{n(x0)} {n(y)}'
    x, up, h = x0, True, wl / 2
    while x < x1:
        d += f'Q{n(x + h / 2)} {n(y - 2 * amp if up else y + 2 * amp)} {n(x + h)} {n(y)}'
        x += h
        up = not up
    return d


REDUCE = '@media (prefers-reduced-motion: reduce){*{animation:none!important}}'
SPIN = '.spin{transform-box:fill-box;transform-origin:center;animation:spin 18s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}'


def svg(w, h, body, label=None, style=''):
    aria = f' role="img" aria-label="{label}"' if label else ' aria-hidden="true"'
    css = f'<style>{style}{REDUCE}</style>' if style else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none"{aria}>'
            f'{css}{body}</svg>\n')


# ---------------------------------------------------------------- hero
def fit(t, size, wght, max_w):
    """The hint as the app shows it: ellipsized to the field's width."""
    if text_width(t, size, wght) <= max_w:
        return t
    while t and text_width(t + '…', size, wght) > max_w:
        t = t[:-1]
    return t.rstrip() + '…'


def cover_cookie(cx, cy, r, grad_id, spin=True):
    """Mini-player cookie: the cover clipped to Cookie12, dimmed, with the pause glyph on top."""
    cls = ' class="spin"' if spin else ''
    g = r * .2
    return (f'<path{cls} d="{SHAPE["cookie12"](cx, cy, r)}" fill="url(#{grad_id})"/>'
            f'<path{cls} d="{SHAPE["cookie12"](cx, cy, r)}" fill="#000" opacity=".32"/>'
            f'<rect x="{n(cx - g * 1.45)}" y="{n(cy - g * 1.6)}" width="{n(g * 1.05)}" height="{n(g * 3.2)}" rx="{n(g * .4)}" fill="#fff"/>'
            f'<rect x="{n(cx + g * .4)}" y="{n(cy - g * 1.6)}" width="{n(g * 1.05)}" height="{n(g * 3.2)}" rx="{n(g * .4)}" fill="#fff"/>')


A_MAIN = 'cubic-bezier(.32,0,.18,1)'
A_SETTLE = 'cubic-bezier(.4,0,.6,1)'


def dock(x, y, k=1.0, fill=SURF, cover='warm'):
    """The dock doing a search, as VibraDock does it. Drawn at hero scale (1.5) and scaled by k.

    Returns (style, defs, body). Browse → Search at 36 %, back at 80 %. The profile never moves,
    the mini-player contracts into its cover cookie, search grows leftwards out of its own
    button, and every pill keeps its 9 dp gap, so nothing merges.
    """
    s = 1.5
    dh = 56 * s
    r, gap = dh / 2, 9 * s
    cy = r
    prof_c = r
    pl_lc = dh + gap + r                          # cookie centre, never moves
    se_rc = 362 * s - r                           # search button, never moves
    pl_rc = se_rc - dh - gap                      # player's right end in Browse
    se_lc = pl_lc + dh + gap                      # search field's left end in Search
    shrink, grow, over = pl_rc - pl_lc, se_rc - se_lc, 8
    T = '8s infinite'
    style = (
        SPIN +
        f'.pc{{animation:pc {T}}}@keyframes pc{{0%,36%{{transform:translateX(0);animation-timing-function:{A_MAIN}}}'
        f'46%{{transform:translateX(-{shrink + over:.1f}px);animation-timing-function:{A_SETTLE}}}'
        f'50%,80%{{transform:translateX(-{shrink:.1f}px);animation-timing-function:{A_MAIN}}}'
        f'89%{{transform:translateX({over}px);animation-timing-function:{A_SETTLE}}}92%,100%{{transform:translateX(0)}}}}'
        f'.pb{{transform-box:fill-box;transform-origin:0 50%;animation:pb {T}}}@keyframes pb{{0%,36%{{transform:scaleX(1);animation-timing-function:{A_MAIN}}}'
        f'46%,80%{{transform:scaleX(0);animation-timing-function:{A_MAIN}}}'
        f'89%{{transform:scaleX({(shrink + over) / shrink:.4f});animation-timing-function:{A_SETTLE}}}92%,100%{{transform:scaleX(1)}}}}'
        f'.sc{{animation:sc {T}}}@keyframes sc{{0%,36%{{transform:translateX(0);animation-timing-function:{A_MAIN}}}'
        f'46%{{transform:translateX(-{grow + over:.1f}px);animation-timing-function:{A_SETTLE}}}'
        f'50%,80%{{transform:translateX(-{grow:.1f}px);animation-timing-function:{A_MAIN}}}89%,100%{{transform:translateX(0)}}}}'
        f'.sb{{transform-box:fill-box;transform-origin:100% 50%;transform:scaleX(0);animation:sb {T}}}'
        f'@keyframes sb{{0%,36%{{transform:scaleX(0);animation-timing-function:{A_MAIN}}}'
        f'46%{{transform:scaleX({(grow + over) / grow:.4f});animation-timing-function:{A_SETTLE}}}'
        f'50%,80%{{transform:scaleX(1);animation-timing-function:{A_MAIN}}}89%,100%{{transform:scaleX(0)}}}}'
        f'.det{{animation:det {T}}}@keyframes det{{0%,36%{{opacity:1}}39%,86%{{opacity:0}}92%,100%{{opacity:1}}}}'
        f'.hint{{opacity:0;animation:hint {T}}}@keyframes hint{{0%,39%{{opacity:0}}48%,80%{{opacity:1}}84%,100%{{opacity:0}}}}'
        '.i1,.i2{transform-box:fill-box;transform-origin:center}'
        f'.i1{{animation:i1 {T}}}@keyframes i1{{0%,36%{{opacity:1;transform:scale(1)}}41%,80%{{opacity:0;transform:scale(.4) rotate(-90deg)}}86%,100%{{opacity:1;transform:scale(1)}}}}'
        f'.i2{{opacity:0;animation:i2 {T}}}@keyframes i2{{0%,38%{{opacity:0;transform:scale(.4) rotate(90deg)}}44%,80%{{opacity:1;transform:scale(1)}}84%,100%{{opacity:0;transform:scale(.4) rotate(90deg)}}}}'
        f'.caret{{opacity:0;animation:caret {T}}}@keyframes caret{{0%,49%{{opacity:0}}50%,56%{{opacity:1}}57%,62%{{opacity:0}}63%,69%{{opacity:1}}70%,75%{{opacity:0}}76%,79%{{opacity:1}}80%,100%{{opacity:0}}}}'
    )
    hint_x = se_lc - r + 20 * s
    hint = fit('Search songs, artists, albums', 24, 400, se_rc - 26 - 12 - hint_x)
    close = 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z'
    defs = (lin('dcover', cover) +
            f'<clipPath id="field"><rect x="{n(hint_x - 6)}" y="0" width="{n(se_rc - 30 - hint_x)}" height="{n(dh)}"/></clipPath>'
            '<filter id="sh" x="-10%" y="-40%" width="120%" height="200%"><feDropShadow dx="0" dy="9" stdDeviation="16" flood-color="#000" flood-opacity=".3"/></filter>')
    b = [f'<g transform="translate({n(x)} {n(y)}) scale({k:.4f})">']
    # each pill is two caps and a body, so its ends travel without being squashed
    b.append(f'<g filter="url(#sh)" fill="{fill}">'
             f'<circle cx="{n(prof_c)}" cy="{n(cy)}" r="{n(r)}"/>'
             f'<circle cx="{n(pl_lc)}" cy="{n(cy)}" r="{n(r)}"/>'
             f'<rect class="pb" x="{n(pl_lc)}" y="0" width="{n(shrink)}" height="{n(dh)}"/>'
             f'<circle class="pc" cx="{n(pl_rc)}" cy="{n(cy)}" r="{n(r)}"/>'
             f'<rect class="sb" x="{n(se_lc)}" y="0" width="{n(grow)}" height="{n(dh)}"/>'
             f'<circle cx="{n(se_rc)}" cy="{n(cy)}" r="{n(r)}"/>'
             f'<circle class="sc" cx="{n(se_rc)}" cy="{n(cy)}" r="{n(r)}"/></g>')
    b.append(f'<path d="{SHAPE["flower"](prof_c, cy, 31)}" fill="{PRIMARY_CONT}"/>' + icon('person', prof_c, cy, 30, ON_PRIMARY_CONT))
    b.append(cover_cookie(pl_lc, cy, 22 * s, 'dcover'))
    tx = pl_lc + 22 * s + 12 * s
    b.append('<g class="det">' + text('Your favourite song', 23, tx, cy - 5, 500)
             + text('Someone you love', 19, tx, cy + 22, 400, fill=ON_VAR) + '</g>')
    b.append('<g clip-path="url(#field)"><g class="sc"><g class="hint">'
             + text(hint, 24, se_rc - r + 20 * s, cy + 8, 400, fill=ON_VAR) + '</g></g></g>')
    b.append(f'<rect class="caret" x="{n(hint_x - 4)}" y="{n(cy - 15)}" width="2.5" height="30" rx="1.25" fill="{PRIMARY}"/>')
    b.append(f'<g class="i1">{icon("search", se_rc, cy, 34, ON)}</g>'
             f'<g class="i2"><path transform="translate({n(se_rc - 17)} {n(cy - 17)}) scale(1.417)" d="{close}" fill="{ON}"/></g>')
    b.append('</g>')
    return style, defs, ''.join(b)


def hero():
    W, H = 1280, 640
    style = (
        '.d1{animation:d1 24s ease-in-out infinite alternate}@keyframes d1{to{transform:translate(-70px,50px)}}'
        '.d2{animation:d2 28s ease-in-out infinite alternate}@keyframes d2{to{transform:translate(90px,-40px)}}'
        '.d3{animation:d3 20s ease-in-out infinite alternate}@keyframes d3{to{transform:translate(-50px,-60px)}}'
    )
    d_style, d_defs, d_body = dock(96, 478)
    tag1 = 'Music that moves like '
    w1 = text_width(tag1, 42, 500)
    b = []
    b.append('<defs><clipPath id="c"><rect width="1280" height="640" rx="40"/></clipPath>'
             + glow('a1', '#6750A4', .62) + glow('a2', '#8C4A60', .5) + glow('a3', '#2B5C8A', .5)
             + glow('g', PRIMARY, .30) + d_defs + '</defs>')
    b.append('<g clip-path="url(#c)">')
    b.append(f'<rect width="{W}" height="{H}" fill="{BG}"/>')
    b.append('<g class="d1"><circle cx="1030" cy="230" r="560" fill="url(#a1)"/></g>')
    b.append('<g class="d2"><circle cx="160" cy="690" r="560" fill="url(#a3)"/></g>')
    b.append('<g class="d3"><circle cx="1250" cy="660" r="430" fill="url(#a2)"/></g>')
    # play button, spinning like PlaybackSpin while music plays
    b.append('<circle cx="985" cy="300" r="320" fill="url(#g)"/>')
    b.append(f'<path class="spin" d="{SHAPE["cookie12"](985, 300, 186)}" fill="{PRIMARY}"/>')
    b.append(f'<rect x="941" y="240" width="34" height="120" rx="12" fill="{ON_PRIMARY}"/>'
             f'<rect x="995" y="240" width="34" height="120" rx="12" fill="{ON_PRIMARY}"/>')
    # logo, wordmark, tagline
    b.append(f'<path transform="translate(98 96) scale(2.3)" d="{LOGO}" fill="{ON}"/>')
    b.append(text('vibra', 172, 88, 334, 700, 40))
    b.append(text(tag1, 42, 96, 404, 500, 0, fill=ON_VAR))
    b.append(text('liquid.', 42, 96 + w1, 404, 600, 40, fill=PRIMARY))
    b.append(d_body)
    b.append('</g>')
    return svg(W, H, ''.join(b), 'vibra: music that moves like liquid', style + d_style)


# ---------------------------------------------------------------- the seek bar used as section divider
def track(fraction, theme='dark', playing=True):
    W, H = 1280, 40
    cy = 20
    played, rest = {'dark': (PRIMARY, 'rgba(230,224,233,.24)'), 'light': (L_PRIMARY, 'rgba(29,27,32,.18)')}[theme]
    x0, x1 = 6, 1274
    tx = x0 + (x1 - x0) * fraction
    amp, wl, sw = 4.5, 42, 6
    b, style = [], ''
    if playing:
        style = '.w{animation:w 1.6s linear infinite}@keyframes w{from{transform:translateX(-42px)}to{transform:translateX(0)}}'
        b.append(f'<defs><clipPath id="p"><rect x="0" y="0" width="{n(tx - 9)}" height="{H}"/></clipPath></defs>')
        b.append(f'<g clip-path="url(#p)"><path class="w" d="{wave_path(x0 - wl, tx, cy, amp, wl)}" stroke="{played}" '
                 f'stroke-width="{sw}" stroke-linecap="round"/></g>')
    else:
        b.append(f'<line x1="{x0}" y1="{cy}" x2="{n(tx - 9)}" y2="{cy}" stroke="{played}" stroke-width="{sw}" stroke-linecap="round"/>')
    b.append(f'<rect x="{n(min(tx, x1) - 3)}" y="{cy - 15}" width="6" height="30" rx="3" fill="{played}"/>')
    if tx + 9 < x1:
        b.append(f'<line x1="{n(tx + 9)}" y1="{cy}" x2="{x1}" y2="{cy}" stroke="{rest}" stroke-width="{sw}" stroke-linecap="round"/>')
    return svg(W, H, ''.join(b), None, style)


# ---------------------------------------------------------------- buttons and logo
def button(label, kind):
    # The pill sits in a transparent band 12 units tall above and below it, so that two
    # buttons wrapping onto two lines on a phone still keep a gap between them.
    h, size, m = 104, 34, 12
    tw_ = text_width(label, size, 600)
    if kind == 'primary':
        fill, fg, ic = PRIMARY, ON_PRIMARY, 'download'
    else:
        fill, fg, ic = '#2B2930', ON, 'arrow'
    pad, isz, gap = 44, 40, 14
    cy = m + h / 2
    if kind == 'primary':
        w = pad + isz + gap + tw_ + pad + 4
        body = (f'<rect y="{m}" width="{n(w)}" height="{h}" rx="{h / 2}" fill="{fill}"/>' + icon(ic, pad + isz / 2, cy, isz, fg)
                + text(label, size, pad + isz + gap, cy + 12, 600, fill=fg))
    else:
        w = pad + tw_ + gap + isz * .8 + pad
        body = (f'<rect y="{m}" width="{n(w)}" height="{h}" rx="{h / 2}" fill="{fill}"/>' + text(label, size, pad, cy + 12, 600, fill=fg)
                + icon(ic, pad + tw_ + gap + isz * .4, cy, isz * .8, fg))
    return svg(int(math.ceil(w)), h + 2 * m, body, label)


def signoff():
    """The last thing on the page, in the hero's language: its colour fields, the wave and the name."""
    W, H = 1280, 360
    style = (
        '.d1{animation:d1 24s ease-in-out infinite alternate}@keyframes d1{to{transform:translate(-70px,40px)}}'
        '.d2{animation:d2 28s ease-in-out infinite alternate}@keyframes d2{to{transform:translate(90px,-30px)}}'
    )
    ls = 2.3
    lw, lh = LOGO_W * ls, LOGO_H * ls
    word = text_width('vibra', 150, 700, 40)
    gap = 34
    x0 = (W - (lw + gap + word)) / 2
    base = 222
    b = ['<defs><clipPath id="c"><rect width="1280" height="360" rx="40"/></clipPath>'
         + glow('a1', '#6750A4', .55) + glow('a3', '#2B5C8A', .5) + '</defs>',
         '<g clip-path="url(#c)">', f'<rect width="{W}" height="{H}" fill="{BG}"/>',
         '<g class="d1"><circle cx="1120" cy="60" r="520" fill="url(#a1)"/></g>',
         '<g class="d2"><circle cx="140" cy="380" r="520" fill="url(#a3)"/></g>',
         f'<path transform="translate({n(x0)} {n(base - 108 + (108 - lh) / 2 + 2)}) scale({ls})" d="{LOGO}" fill="{ON}"/>',
         text('vibra', 150, x0 + lw + gap, base, 700, 40), '</g>']
    return svg(W, H, ''.join(b), 'vibra', style)


# ---------------------------------------------------------------- small shapes for the principles
def principle(shape, grad, anim):
    style = {
        'spin': SPIN,
        'breathe': '.b{transform-box:fill-box;transform-origin:center;animation:b 3.2s ease-in-out infinite alternate}@keyframes b{to{transform:scale(.9)}}',
        'press': '.p{transform-box:fill-box;transform-origin:center;animation:p 2.4s infinite}@keyframes p{0%,60%{transform:scale(1)}70%{transform:scale(.9)}80%{transform:scale(1.04)}88%,100%{transform:scale(1)}}',
    }[anim]
    cls = {'spin': 'spin', 'breathe': 'b', 'press': 'p'}[anim]
    body = f'<defs>{lin("g", grad)}</defs><path class="{cls}" d="{SHAPE[shape](48, 48, 44)}" fill="url(#g)"/>'
    return svg(96, 96, body, None, style)


# ---------------------------------------------------------------- feature cards
def card(title, tint, tint_at, inner, style=''):
    W, H = 800, 360
    tcol = {'lavender': '#6750A4', 'pink': '#8C4A60', 'blue': '#2B5C8A', 'warm': '#B3261E'}[tint]
    b = [f'<defs><clipPath id="c"><rect width="{W}" height="{H}" rx="40"/></clipPath>{glow("t", tcol, .55)}</defs>',
         '<g clip-path="url(#c)">', f'<rect width="{W}" height="{H}" fill="{SURF}"/>',
         f'<circle cx="{tint_at[0]}" cy="{tint_at[1]}" r="460" fill="url(#t)"/>', inner,
         text(title, 44, 44, 316, 700, 40), '</g>']
    return svg(W, H, ''.join(b), title, style)


def feature_flow():
    """The same search the hero shows, larger: the dock as it behaves in the app."""
    k = 1.9 / 1.5
    w = 362 * 1.9
    d_style, d_defs, d_body = dock((800 - w) / 2, 128 - 28 * 1.9, k, SURF_HIGH, 'blue')
    return card('Liquid to the touch.', 'lavender', (760, 20), f'<defs>{d_defs}</defs>{d_body}', d_style)


def feature_player():
    style = '.w{animation:w 1.6s linear infinite}@keyframes w{from{transform:translateX(-42px)}to{transform:translateX(0)}}'
    accent = '#FFB59A'
    x0, tx, x1, wy = 300, 560, 756, 96
    inner = (f'<defs>{lin("art", "warm")}<clipPath id="wp"><rect x="{x0 - 4}" y="0" width="{tx - 9 - x0 + 4}" height="360"/></clipPath></defs>'
             f'<circle cx="160" cy="150" r="300" fill="#E2541B" opacity=".18"/>'
             f'<rect x="44" y="36" width="210" height="210" rx="28" fill="url(#art)"/>'
             f'<circle cx="190" cy="104" r="40" fill="{accent}" opacity=".85"/>'
             f'<path d="M44 200 Q100 168 150 196 T254 186 V218 A28 28 0 0 1 226 246 H72 A28 28 0 0 1 44 218Z" fill="#14213D" opacity=".55"/>'
             f'<g clip-path="url(#wp)"><path class="w" d="{wave_path(x0 - 42, tx, wy, 4.5, 42)}" stroke="{accent}" stroke-width="6" stroke-linecap="round"/></g>'
             f'<rect x="{tx - 3}" y="{wy - 15}" width="6" height="30" rx="3" fill="{accent}"/>'
             f'<line x1="{tx + 9}" y1="{wy}" x2="{x1}" y2="{wy}" stroke="rgba(255,255,255,.24)" stroke-width="6" stroke-linecap="round"/>'
             + text('1:24', 22, x0, wy + 40, 500, fill='rgba(255,255,255,.64)', tnum=True)
             + text('3:00', 22, x1, wy + 40, 500, anchor='end', fill='rgba(255,255,255,.64)', tnum=True)
             + icon('prev', 420, 214, 40, ON) +
             f'<path class="spin" d="{SHAPE["cookie12"](528, 214, 44)}" fill="{accent}"/>'
             f'<rect x="515" y="198" width="9" height="32" rx="3.5" fill="#5A1A00"/><rect x="532" y="198" width="9" height="32" rx="3.5" fill="#5A1A00"/>'
             + icon('next', 636, 214, 40, ON))
    return card('Painted by the cover.', 'warm', (780, 340), inner, style + SPIN)


def feature_lyrics():
    style = ('.sweep{transform-box:fill-box;transform-origin:0 50%;animation:sw 6s infinite}'
             '@keyframes sw{0%{transform:scaleX(0)}70%,90%{transform:scaleX(1)}100%{transform:scaleX(1)}}')
    l1 = 'Streetlights hum a quiet tune'
    l2 = "and we're dancing in the rain"
    l3 = 'nothing else is on my mind'
    w2 = text_width(l2, 40, 700, 40)
    inner = (text(l1, 30, 44, 78, 700, 40, fill='rgba(230,224,233,.32)')
             + text(l2, 40, 44, 152, 700, 40, fill='rgba(230,224,233,.38)')
             + f'<defs><clipPath id="sg"><rect class="sweep" x="40" y="100" width="{n(w2 + 8)}" height="70"/></clipPath></defs>'
             + f'<g clip-path="url(#sg)">{text(l2, 40, 44, 152, 700, 40, fill="#FFFFFF")}</g>'
             + text(l3, 30, 44, 222, 700, 40, fill='rgba(230,224,233,.32)'))
    return card('Every word, on time.', 'blue', (760, 40), inner, style)


def feature_blocks():
    inner = (f'<defs>{lin("fy", "lavender", "1", "0", "0", "1")}{lin("ck", "pink")}{lin("sb", "blue")}</defs>'
             f'<rect x="44" y="32" width="330" height="214" rx="30" fill="{PRIMARY_CONT}"/>'
             f'<path d="{SHAPE["cookie9"](290, 112, 66)}" fill="url(#fy)"/>'
             + text('For You', 30, 68, 218, 700, 40) +
             f'<path d="{SHAPE["cookie12"](334, 206, 24)}" fill="{PRIMARY}"/>' + icon('play', 335, 206, 22, ON_PRIMARY) +
             f'<rect x="388" y="32" width="176" height="102" rx="26" fill="{SURF_HIGH}"/>'
             f'<path d="{SHAPE["sunny"](476, 83, 36)}" fill="url(#ck)"/>'
             f'<rect x="578" y="32" width="178" height="102" rx="26" fill="{SURF_HIGH}"/>'
             f'<path d="{SHAPE["puffy"](667, 83, 36)}" fill="url(#sb)"/>'
             f'<rect x="388" y="144" width="368" height="102" rx="26" fill="{SURF_HIGH}"/>'
             f'<rect x="404" y="164" width="62" height="62" rx="16" fill="url(#ck)"/>'
             f'<rect x="482" y="178" width="180" height="13" rx="6.5" fill="{ON}"/>'
             f'<rect x="482" y="202" width="118" height="11" rx="5.5" fill="{OUTLINE}"/>')
    return card('A home that knows you.', 'lavender', (40, 360), inner)


def feature_shapes():
    style = (SPIN + '.rs{transform-box:fill-box;transform-origin:center;animation:spin 24s linear infinite reverse}'
             '.pulse{transform-box:fill-box;transform-origin:center;animation:pl 1.6s ease-in-out infinite}'
             '@keyframes pl{0%,100%{transform:scale(1)}15%{transform:scale(1.08)}30%{transform:scale(.98)}45%{transform:scale(1.05)}}'
             '.br{transform-box:fill-box;transform-origin:center;animation:br 3.4s ease-in-out infinite alternate}@keyframes br{to{transform:scale(.88)}}'
             '.sway{transform-box:fill-box;transform-origin:center;animation:sy 4s ease-in-out infinite alternate}@keyframes sy{from{transform:rotate(-10deg)}to{transform:rotate(10deg)}}')
    items = [('boom', 'warm', 'spin', 'energy'), ('puffy', 'blue', 'br', 'calm'), ('heart', 'pink', 'pulse', 'love'),
             ('gem', 'cool', 'sway', 'focus'), ('star', 'lavender', 'rs', 'favourites')]
    defs = ''.join(lin(f'g{i}', g) for i, (_, g, _, _) in enumerate(items))
    inner = f'<defs>{defs}</defs>'
    for i, (shape, _, cls, label) in enumerate(items):
        cx = 112 + i * 144
        inner += f'<path class="{cls}" d="{SHAPE[shape](cx, 116, 54)}" fill="url(#g{i})"/>'
        inner += text(label, 24, cx, 218, 500, anchor='middle', fill=ON_VAR)
    return card('Every mood has a shape.', 'pink', (780, 20), inner, style)


def feature_engine():
    heights = [70, 120, 170, 140, 196, 110, 160, 90, 150, 80]
    style = '.eq{transform-box:fill-box;transform-origin:50% 100%;animation:eq 1.3s ease-in-out infinite alternate}@keyframes eq{from{transform:scaleY(.3)}to{transform:scaleY(1)}}'
    inner = ('<defs><linearGradient id="eqg" gradientUnits="userSpaceOnUse" x1="0" y1="40" x2="0" y2="246">'
             f'<stop offset="0" stop-color="{PRIMARY}"/><stop offset="1" stop-color="{PRIMARY_CONT}"/></linearGradient></defs>')
    for i, h in enumerate(heights):
        x = 44 + i * 44
        inner += f'<rect class="eq" style="animation-delay:-{(i * 0.37) % 1.3:.2f}s" x="{x}" y="{246 - h}" width="30" height="{h}" rx="15" fill="url(#eqg)"/>'
    chips = ['YouTube Music', 'Spotify import', 'Local files', 'Synced lyrics', 'Cast · Last.fm']
    for i, c in enumerate(chips):
        y = 26 + i * 50
        inner += f'<rect x="508" y="{y}" width="248" height="42" rx="21" fill="{SURF_HIGH}"/>'
        inner += text(c, 21, 632, y + 28, 500, anchor='middle', fill=ON)
    return card('Serious under the surface.', 'blue', (40, 0), inner, style)


# ---------------------------------------------------------------- showcase of real screens
def jpeg_data(path, width=500, quality=80):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    im = im.resize((width, round(h * width / w)), Image.LANCZOS)
    bio = io.BytesIO()
    im.save(bio, 'JPEG', quality=quality, optimize=True, progressive=True)
    return 'data:image/jpeg;base64,' + base64.b64encode(bio.getvalue()).decode()


def showcase(shots):
    W, H = 1280, 780
    pw, ph = 250, 556
    gap = 50
    x0 = (W - (4 * pw + 3 * gap)) / 2
    tops = [104, 64, 104, 64]
    b = [f'<defs><clipPath id="c"><rect width="{W}" height="{H}" rx="40"/></clipPath>'
         + glow('a1', '#6750A4', .55) + glow('a2', '#8C4A60', .45) + glow('a3', '#2B5C8A', .45) +
         '<filter id="sh" x="-30%" y="-20%" width="160%" height="150%"><feDropShadow dx="0" dy="26" stdDeviation="26" flood-color="#000" flood-opacity=".5"/></filter>']
    for i in range(4):
        x, y = x0 + i * (pw + gap), tops[i]
        b.append(f'<clipPath id="s{i}"><rect x="{n(x + 8)}" y="{y + 8}" width="{pw - 16}" height="{ph - 16}" rx="30"/></clipPath>')
    b.append('</defs><g clip-path="url(#c)">')
    b.append(f'<rect width="{W}" height="{H}" fill="{BG}"/>'
             '<circle cx="200" cy="200" r="520" fill="url(#a1)"/><circle cx="1150" cy="300" r="520" fill="url(#a2)"/>'
             '<circle cx="660" cy="820" r="520" fill="url(#a3)"/>')
    for i, (src, label) in enumerate(shots):
        x, y = x0 + i * (pw + gap), tops[i]
        b.append(f'<rect filter="url(#sh)" x="{n(x)}" y="{y}" width="{pw}" height="{ph}" rx="38" fill="#0F0D13" stroke="#36343B" stroke-width="2"/>')
        b.append(f'<image clip-path="url(#s{i})" x="{n(x + 8)}" y="{y + 8}" width="{pw - 16}" height="{ph - 16}" '
                 f'preserveAspectRatio="xMidYMid slice" href="{src}"/>')
        b.append(text(label, 26, x + pw / 2, y + ph + 50, 600, 40, anchor='middle', fill=ON))
    b.append('</g>')
    return svg(W, H, ''.join(b), 'vibra screens: ' + ', '.join(l for _, l in shots))
