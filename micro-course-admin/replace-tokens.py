#!/usr/bin/env python3
"""
Token replacer for student views
Replaces hardcoded px values with design tokens per docs/DESIGN.md v1.1
"""

import re
import os
import sys

# ─── Replacement Maps ──────────────────────────────────────────────────────────

# Spacing: px → var(--space-N)
# Rules: margin/padding/gap all replaced
SPACING = {
    0: None,    # skip 0
    2: 'var(--space-1)',    # 4px → space-1, but 2px doesn't exist in system
    4: 'var(--space-1)',
    8: 'var(--space-2)',
    12: 'var(--space-3)',
    16: 'var(--space-4)',
    20: 'var(--space-5)',
    24: 'var(--space-6)',
    32: 'var(--space-7)',
    48: 'var(--space-8)',
    64: 'var(--space-9)',
}

# Border radius
RADIUS = {
    0: None,
    2: 'var(--radius-sm)',
    3: 'var(--radius-sm)',
    4: 'var(--radius-sm)',
    6: 'var(--radius-sm)',
    8: 'var(--radius-md)',
    12: 'var(--radius-lg)',
    16: 'var(--radius-xl)',
    20: 'var(--radius-2xl)',
    50: 'var(--radius-circle)',   # special
    9999: 'var(--radius-pill)',
}

# Font size
FONT_SIZE = {
    10: 'var(--text-xs)',
    11: 'var(--text-xs)',
    12: 'var(--text-xs)',
    13: 'var(--text-sm)',
    14: 'var(--text-base)',
    15: 'var(--text-base)',
    16: 'var(--text-md)',
    18: 'var(--text-lg)',
    20: 'var(--text-xl)',
    22: 'var(--text-2xl)',
    24: 'var(--text-2xl)',
    32: 'var(--text-3xl)',
    36: '36px',   # keep — score number
    48: '48px',   # keep — empty icon
}

# Font weight
FONT_WEIGHT = {
    400: 'var(--weight-regular)',
    500: 'var(--weight-medium)',
    600: 'var(--weight-semibold)',
    700: 'var(--weight-bold)',
}

# Line height
LINE_HEIGHT = {
    1.0: '1',
    1.2: '1.2',
    1.25: 'var(--leading-tight)',
    1.3: '1.3',
    1.375: 'var(--leading-snug)',
    1.4: '1.4',
    1.5: 'var(--leading-normal)',
    1.6: 'var(--leading-relaxed)',
    1.625: 'var(--leading-relaxed)',
    1.8: '1.8',
    2.0: '2',
    2.2: '2.2',
}

# ─── Don't touch these ────────────────────────────────────────────────────────
# - 768px media queries
# - 100%, 50% widths/heights
# - translateY(-2px) animation
# - 1px borders
# - color values (rgba, hex)
# - z-index
# - inset/grid values
# - gradient angles (135deg)
# - El slider runway height (4px, 6px)


def token_replace(content):
    """Apply all token replacements, return (new_content, count)."""
    changes = 0

    # ── line-height ────────────────────────────────────────────────────────────
    for px, token in LINE_HEIGHT.items():
        # Match: line-height: {px}; or line-height: {px}
        patterns = [
            rf'line-height:\s*{re.escape(str(px))};',
            rf'line-height:\s*{re.escape(str(px))}\s*!important;',
        ]
        for pat in patterns:
            new = f'line-height: {token};'
            if re.search(pat, content):
                content = re.sub(pat, new, content)
                changes += 1

    # ── font-weight ───────────────────────────────────────────────────────────
    for px, token in FONT_WEIGHT.items():
        patterns = [
            rf'font-weight:\s*{px};',
            rf'font-weight:\s*{px}\s*!important;',
        ]
        for pat in patterns:
            new = f'font-weight: {token};'
            if re.search(pat, content):
                content = re.sub(pat, new, content)
                changes += 1

    # ── font-size ─────────────────────────────────────────────────────────────
    # Be careful not to match color hexes like #ff6482 (6 digits)
    # We match font-size specifically
    for px, token in FONT_SIZE.items():
        if token == f'{px}px':
            continue  # skip kept values
        patterns = [
            rf'font-size:\s*{px}px;',
            rf'font-size:\s*{px}px\s*!important;',
            rf'font-size:\s*{px}px\s+',
        ]
        for pat in patterns:
            if re.search(pat, content):
                content = re.sub(pat, f'font-size: {token};', content)
                changes += 1

    # ── border-radius ────────────────────────────────────────────────────────
    for px, token in RADIUS.items():
        if token is None:
            continue
        patterns = [
            rf'border-radius:\s*{px}px;',
            rf'border-radius:\s*{px}px\s*!important;',
        ]
        for pat in patterns:
            if re.search(pat, content):
                content = re.sub(pat, f'border-radius: {token};', content)
                changes += 1

    # Special: 50% → circle
    if re.search(r'border-radius:\s*50%;', content):
        content = re.sub(r'border-radius:\s*50%;', 'border-radius: var(--radius-circle);', content)
        changes += 1

    # Special: 9999px → pill
    if re.search(r'border-radius:\s*9999px;', content):
        content = re.sub(r'border-radius:\s*9999px;', 'border-radius: var(--radius-pill);', content)
        changes += 1

    # ── Spacing: margin/padding/gap ──────────────────────────────────────────
    # Multi-value patterns (most specific first)
    multi_value_padding = [
        (r'padding:\s*0\s+48px;',           'padding: 0 var(--space-8);'),
        (r'padding:\s*0\s+16px;',            'padding: 0 var(--space-4);'),
        (r'padding:\s*0\s+12px;',            'padding: 0 var(--space-3);'),
        (r'padding:\s*16px\s+20px;',        'padding: var(--space-4) var(--space-5);'),
        (r'padding:\s*16px\s+16px;',         'padding: var(--space-4) var(--space-4);'),
        (r'padding:\s*12px\s+16px;',        'padding: var(--space-3) var(--space-4);'),
        (r'padding:\s*12px\s+12px;',        'padding: var(--space-3) var(--space-3);'),
        (r'padding:\s*8px\s+12px;',         'padding: var(--space-2) var(--space-3);'),
        (r'padding:\s*4px\s+8px;',           'padding: var(--space-1) var(--space-2);'),
        (r'padding:\s*6px\s+12px;',          'padding: var(--space-2) var(--space-3);'),
        (r'padding:\s*10px\s+12px;',        'padding: var(--space-3) var(--space-3);'),
        (r'padding:\s*14px\s+16px;',         'padding: var(--space-3) var(--space-4);'),
        (r'padding:\s*24px\s+16px;',         'padding: var(--space-6) var(--space-4);'),
        (r'padding:\s*20px\s+24px;',         'padding: var(--space-5) var(--space-6);'),
        (r'padding:\s*32px\s+16px;',         'padding: var(--space-7) var(--space-4);'),
        (r'padding:\s*32px\s+0;',            'padding: var(--space-7) 0;'),
        (r'padding:\s*0\s+48px;',            'padding: 0 var(--space-8);'),
        (r'padding:\s*60px\s+0;',            'padding: var(--space-8) 0;'),
        (r'padding:\s*48px\s+0;',            'padding: var(--space-8) 0;'),
        (r'padding:\s*8px\s+0;',             'padding: var(--space-2) 0;'),
        (r'padding:\s*4px\s+0;',             'padding: var(--space-1) 0;'),
    ]

    for pat, replacement in multi_value_padding:
        if re.search(pat, content):
            content = re.sub(pat, replacement, content)
            changes += 1

    # Single-value padding (only the most common ones that need replacing)
    single_padding = [
        (r'padding:\s*48px;',               'padding: var(--space-8);'),
        (r'padding:\s*32px;',               'padding: var(--space-7);'),
        (r'padding:\s*24px;',               'padding: var(--space-6);'),
        (r'padding:\s*20px;',               'padding: var(--space-5);'),
        (r'padding:\s*16px;',               'padding: var(--space-4);'),
        (r'padding:\s*14px;',               'padding: var(--space-3);'),
        (r'padding:\s*12px;',               'padding: var(--space-3);'),
        (r'padding:\s*10px;',               'padding: var(--space-3);'),
        (r'padding:\s*8px;',                'padding: var(--space-2);'),
        (r'padding:\s*6px;',                'padding: var(--space-2);'),
        (r'padding:\s*4px;',                'padding: var(--space-1);'),
    ]
    for pat, replacement in single_padding:
        if re.search(pat, content):
            content = re.sub(pat, replacement, content)
            changes += 1

    # Multi-value margin
    multi_value_margin = [
        (r'margin:\s*0\s+0\s+12px;',           'margin: 0 0 var(--space-3);'),
        (r'margin:\s*0\s+0\s+16px;',           'margin: 0 0 var(--space-4);'),
        (r'margin:\s*0\s+0\s+20px;',           'margin: 0 0 var(--space-5);'),
        (r'margin:\s*0\s+0\s+4px;',            'margin: 0 0 var(--space-1);'),
        (r'margin:\s*0\s+0\s+8px;',            'margin: 0 0 var(--space-2);'),
        (r'margin:\s*0\s+0\s+6px;',            'margin: 0 0 var(--space-2);'),
        (r'margin:\s*0\s+0\s+2px;',            'margin: 0 0 2px;'),
        (r'margin:\s*0\s+0\s+24px;',           'margin: 0 0 var(--space-6);'),
        (r'margin:\s*0\s+0\s+32px;',           'margin: 0 0 var(--space-7);'),
        (r'margin:\s*0\s+0\s+14px;',           'margin: 0 0 var(--space-3);'),
        (r'margin:\s*0\s+0\s+10px;',           'margin: 0 0 var(--space-3);'),
        (r'margin:\s*0\s+0\s+40px;',           'margin: 0 0 40px;'),  # not in system
        (r'margin:\s*0\s+0\s+30px;',           'margin: 0 0 30px;'),  # not in system
        (r'margin:\s*12px\s+0\s+0;',           'margin: var(--space-3) 0 0;'),
        (r'margin:\s*16px\s+0\s+0;',           'margin: var(--space-4) 0 0;'),
        (r'margin:\s*8px\s+0\s+0;',            'margin: var(--space-2) 0 0;'),
        (r'margin:\s*4px\s+0\s+0;',            'margin: var(--space-1) 0 0;'),
        (r'margin:\s*0\s+auto\s+24px;',        'margin: 0 auto var(--space-6);'),
        (r'margin:\s*0\s+auto\s+16px;',        'margin: 0 auto var(--space-4);'),
        (r'margin:\s*0\s+auto\s+12px;',        'margin: 0 auto var(--space-3);'),
        (r'margin:\s*0\s+auto\s+20px;',        'margin: 0 auto var(--space-5);'),
        (r'margin:\s*0\s+auto\s+8px;',        'margin: 0 auto var(--space-2);'),
        (r'margin:\s*24px\s+0;',               'margin: var(--space-6) 0;'),
        (r'margin:\s*20px\s+0;',               'margin: var(--space-5) 0;'),
        (r'margin:\s*16px\s+0;',               'margin: var(--space-4) 0;'),
        (r'margin:\s*0\s+auto;',               'margin: 0 auto;'),
    ]
    for pat, replacement in multi_value_margin:
        if re.search(pat, content):
            content = re.sub(pat, replacement, content)
            changes += 1

    # Single-value margin (careful not to break margin: 0 etc.)
    single_margin = [
        (r'margin-bottom:\s*48px;',            'margin-bottom: var(--space-8);'),
        (r'margin-bottom:\s*32px;',            'margin-bottom: var(--space-7);'),
        (r'margin-bottom:\s*24px;',            'margin-bottom: var(--space-6);'),
        (r'margin-bottom:\s*20px;',            'margin-bottom: var(--space-5);'),
        (r'margin-bottom:\s*16px;',            'margin-bottom: var(--space-4);'),
        (r'margin-bottom:\s*14px;',            'margin-bottom: var(--space-3);'),
        (r'margin-bottom:\s*12px;',            'margin-bottom: var(--space-3);'),
        (r'margin-bottom:\s*10px;',            'margin-bottom: var(--space-3);'),
        (r'margin-bottom:\s*8px;',             'margin-bottom: var(--space-2);'),
        (r'margin-bottom:\s*6px;',             'margin-bottom: var(--space-2);'),
        (r'margin-bottom:\s*4px;',             'margin-bottom: var(--space-1);'),
        (r'margin-top:\s*24px;',               'margin-top: var(--space-6);'),
        (r'margin-top:\s*20px;',               'margin-top: var(--space-5);'),
        (r'margin-top:\s*16px;',               'margin-top: var(--space-4);'),
        (r'margin-top:\s*14px;',               'margin-top: var(--space-3);'),
        (r'margin-top:\s*12px;',               'margin-top: var(--space-3);'),
        (r'margin-top:\s*10px;',               'margin-top: var(--space-3);'),
        (r'margin-top:\s*8px;',                'margin-top: var(--space-2);'),
        (r'margin-top:\s*6px;',                'margin-top: var(--space-2);'),
        (r'margin-top:\s*4px;',               'margin-top: var(--space-1);'),
        (r'margin-left:\s*16px;',              'margin-left: var(--space-4);'),
        (r'margin-left:\s*12px;',              'margin-left: var(--space-3);'),
        (r'margin-left:\s*8px;',               'margin-left: var(--space-2);'),
        (r'margin-left:\s*4px;',               'margin-left: var(--space-1);'),
        (r'margin-right:\s*12px;',             'margin-right: var(--space-3);'),
        (r'margin-right:\s*8px;',              'margin-right: var(--space-2);'),
        (r'margin-right:\s*4px;',             'margin-right: var(--space-1);'),
    ]
    for pat, replacement in single_margin:
        if re.search(pat, content):
            content = re.sub(pat, replacement, content)
            changes += 1

    # gap values
    gap_patterns = [
        (r'gap:\s*24px;',       'gap: var(--space-6);'),
        (r'gap:\s*20px;',       'gap: var(--space-5);'),
        (r'gap:\s*16px;',       'gap: var(--space-4);'),
        (r'gap:\s*14px;',       'gap: var(--space-3);'),
        (r'gap:\s*12px;',       'gap: var(--space-3);'),
        (r'gap:\s*10px;',       'gap: var(--space-3);'),
        (r'gap:\s*8px;',        'gap: var(--space-2);'),
        (r'gap:\s*6px;',        'gap: var(--space-2);'),
        (r'gap:\s*4px;',        'gap: var(--space-1);'),
        (r'gap:\s*2px;',        'gap: 2px;'),
        (r'gap:\s*3px;',        'gap: 3px;'),
    ]
    for pat, replacement in gap_patterns:
        if re.search(pat, content):
            content = re.sub(pat, replacement, content)
            changes += 1

    # ── Specific spacing values for special properties ────────────────────────
    # top/right/bottom/left values (not all — only those in design system)
    # These are typically small, intentional values — only replace known spacing values
    side_position_patterns = [
        # top values that match spacing tokens
        (r'top:\s*var\(--space-4\)', None),  # already token, skip
    ]
    # Don't auto-replace top/right/bottom/left - too risky with specific pixel values

    return content, changes


def main():
    base = '/Users/jackie/微课平台/micro-course-admin'

    files = [
        'src/views/student/VideoPlayer.vue',
        'src/views/student/ExerciseTake.vue',
        'src/views/student/CourseDetail.vue',
        'src/views/student/MyCourses.vue',
        'src/views/student/WeeklyReport.vue',
        'src/views/student/CourseSquare.vue',
        'src/views/student/MyReviews.vue',
        'src/views/student/Profile.vue',
        'src/views/student/LearningCenter.vue',
    ]

    for fname in files:
        fpath = os.path.join(base, fname)
        if not os.path.exists(fpath):
            print(f'SKIP (not found): {fname}')
            continue

        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()

        new_content, count = token_replace(content)

        if count > 0:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f'✓ {fname}: {count} replacements')
        else:
            print(f'○ {fname}: no changes')


if __name__ == '__main__':
    main()
