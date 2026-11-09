#!/usr/bin/env python3
"""WCAG contrast audit for every foreground/background pairing in the Android app.

Run after any color change: python3 scripts/contrast_audit.py  (exit 1 on failure; -v lists passes)
Text needs 4.5:1, large text / icons / progress bars 3:1. Values mirror
ui/theme/Color.kt (AppPalette + Light/DarkColorScheme). Dynamic color (Material
You) is off so these are the colors users actually see. Android counterpart of
the iOS repo's scripts/contrast_audit.py — add a row when a view gets a new
color pairing.
"""
import sys

def hx(h, a=1.0):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def over(fg, bg):
    a = fg[3]
    return tuple(round(fg[i] * a + bg[i] * (1 - a)) for i in range(3)) + (1,)

def tint(c, a):
    return c[:3] + (a,)

def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2])

def ratio(fg, bg):
    l1, l2 = lum(over(fg, bg)), lum(bg)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

MODES = {
    'light': dict(
        bg=hx('F8F0FF'), surface=hx('F8F0FF'), surfaceVariant=hx('E9DFF0'),
        onSurface=hx('1D1B20'), onSurfaceVariant=hx('49454F'),
        primary=hx('7B2D8B'), error=hx('B3261E'),
        secContainer=hx('F3DAF5'), onSecContainer=hx('2E1033'),
        errContainer=hx('F9DEDC'), onErrContainer=hx('410E0B'),
        accent=hx('7B2D8B'), sec=hx('5E5866'), err=hx('C4001A'), warn=hx('A84300'),
        rec=hx('C4001A'), fill=hx('7B2D8B')),
    'dark': dict(
        bg=hx('141218'), surface=hx('211D26'), surfaceVariant=hx('2E2934'),
        onSurface=hx('E6E0E9'), onSurfaceVariant=hx('CAC4D0'),
        primary=hx('E0B6EE'), error=hx('F2B8B5'),
        secContainer=hx('5A2A63'), onSecContainer=hx('F7D8FF'),
        errContainer=hx('8C1D18'), onErrContainer=hx('F9DEDC'),
        accent=hx('E0B6EE'), sec=hx('B5AEBD'), err=hx('FF6961'), warn=hx('FFB340'),
        rec=hx('C4001A'), fill=hx('7B2D8B')),
}
WHITE, BLACK = hx('FFFFFF'), hx('000000')
# Header gradient (all modes): BrandPurple -> HeaderPink
GRAD = [hx('7B2D8B'), hx('B0186B')]
ORANGE = hx('FB8C00')

def rows(S):
    A = S['accent']
    return [
        ('Header title / subtitle / icons / history count (white on gradient)', WHITE, GRAD, 4.5),
        ('Record / Translate / Switch button (white on fill)', WHITE, [S['fill']], 4.5),
        ('Stop Recording (white on red)', WHITE, [S['rec']], 4.5),
        ('Recording hint / progress text', S['sec'], [S['bg'], S['surface']], 4.5),
        ('Recording progress bar (red / warning)', S['err'], [S['surface']], 3),
        ('Recording 10s-left warning text', S['warn'], [S['surface']], 4.5),
        ('Direction indicator text', A, [S['surfaceVariant']], 4.5),
        ('Auto-detect chip', S['onSecContainer'], [S['secContainer']], 4.5),
        ('Card title / speaker / chosen thumb', A, [S['surface']], 4.5),
        ('Card result text', S['onSurface'], [S['surface']], 4.5),
        ('Card feedback prompt / thumb not chosen / speaker disabled', S['sec'], [S['surface']], 4.5),
        ('HD chip', A, [over(tint(A, 0.12), S['surface'])], 4.5),
        ('HD chip empty (black on orange)', BLACK, [ORANGE], 4.5),
        ('Processing card', A, [over(tint(A, 0.08), S['bg'])], 4.5),
        ('Status card', S['onSecContainer'], [S['secContainer']], 4.5),
        ('Error card', S['onErrContainer'], [S['errContainer']], 4.5),
        ('Secondary text (all screens)', S['sec'], [S['bg'], S['surface'], S['surfaceVariant']], 4.5),
        ('onSurfaceVariant text (Settings, History)', S['onSurfaceVariant'], [S['bg'], S['surface']], 4.5),
        ('Settings headers / checkmarks / radio', S['primary'], [S['bg']], 4.5),
        ('Error text (Settings TTS error, Clear All)', S['error'], [S['bg']], 4.5),
        ('History / Phrasebook accent icons + category', A, [S['bg'], S['surface']], 4.5),
        ('Phrasebook row (on surfaceVariant 60%)', S['onSurface'], [over(tint(S['surfaceVariant'], 0.6), S['bg'])], 4.5),
        ('Phrasebook row subtitle', S['onSurfaceVariant'], [over(tint(S['surfaceVariant'], 0.6), S['bg'])], 4.5),
        ('Phrasebook speaker', A, [over(tint(S['surfaceVariant'], 0.6), S['bg'])], 3),
    ]

failures = 0
for mode, S in MODES.items():
    for name, fg, bgs, need in rows(S):
        for bg in bgs:
            r = ratio(fg, bg)
            if r < need:
                failures += 1
                print(f'FAIL {mode:5s} {r:5.2f} (need {need}) {name}')
            elif '-v' in sys.argv:
                print(f' ok  {mode:5s} {r:5.2f} (need {need}) {name}')
print(f'{failures} failure(s)')
sys.exit(1 if failures else 0)
