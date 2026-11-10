# 產生 APK-Weather 的向量圖示與小工具背景（色票依 docs/DESIGN.md 第 2 節）
import colorsys, math, os, sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

def hsl(h, s, l, a=1.0):
    r, g, b = colorsys.hls_to_rgb(h / 360, l / 100, s / 100)
    return "#%02X%02X%02X%02X" % (round(a * 255), round(r * 255), round(g * 255), round(b * 255))

def write(name, body):
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + body + "\n")

def vector(name, vp, paths, size=None):
    size = size or vp
    inner = "\n".join(paths)
    write(name, f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{vp}" android:viewportHeight="{vp}">
{inner}
</vector>''')

def fill(d, c, extra=""):
    return f'    <path android:fillColor="{c}" android:pathData="{d}" {extra}/>'

def stroke(d, c, w=1.7):
    return f'    <path android:strokeColor="{c}" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{d}" />'

# ---------- 天氣圖示（viewport 48） ----------
SUN_FILL = hsl(42, 95, 62)
SUN_RAY = hsl(42, 95, 66)
CLOUD = "M14,38h22a8,8 0 0,0 0,-16a11,11 0 0,0 -21,-2.5A8.5,8.5 0 0,0 14,38z"

def rays(cx=24, cy=24, r1=19, r2=14.5):
    out = []
    for a in range(0, 360, 45):
        t = math.radians(a)
        out.append("M%.2f,%.2fL%.2f,%.2f" % (cx + r1 * math.sin(t), cy - r1 * math.cos(t), cx + r2 * math.sin(t), cy - r2 * math.cos(t)))
    return "".join(out)

def circle(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z"

def shift_path(d, dx, dy, k):
    # 只用在雲朵：把絕對座標的 CLOUD 縮放平移（手動版，避免 group transform 在 Glance 不支援的疑慮）
    return d

sun = [stroke(rays(), SUN_RAY, 2.4), fill(circle(24, 24, 10.5), SUN_FILL)]
vector("ic_w_sun", 48, sun)

cloud_white = hsl(220, 30, 94)
cloud_line = hsl(220, 20, 72)
def cloud(c=cloud_white, line=cloud_line, ty=0):
    g = f'    <group android:translateY="{ty}">\n' + fill(CLOUD, c, f'android:strokeColor="{line}" android:strokeWidth="1"') + "\n    </group>"
    return g

partly_sun = f'''    <group android:translateX="-6" android:translateY="-7" android:scaleX="0.85" android:scaleY="0.85">
{stroke(rays(), SUN_RAY, 2.4)}
{fill(circle(24, 24, 10.5), SUN_FILL)}
    </group>'''
vector("ic_w_partly", 48, [partly_sun, '    <group android:translateX="4" android:translateY="4" android:scaleX="0.9" android:scaleY="0.9">\n' + fill(CLOUD, cloud_white, f'android:strokeColor="{cloud_line}" android:strokeWidth="1"') + "\n    </group>"])
vector("ic_w_cloud", 48, [cloud(hsl(220, 25, 88), hsl(220, 20, 70))])
drops = stroke("M17,36L14.5,42M25,36L22.5,42M33,36L30.5,42", hsl(205, 85, 65), 2.4)
vector("ic_w_rain", 48, [cloud(hsl(220, 18, 78), hsl(220, 16, 60), -6), drops])
vector("ic_w_thunder", 48, [cloud(hsl(220, 14, 70), hsl(220, 14, 55), -6), fill("M25,30L19,40h5l-2,7l8,-11h-5l3,-6z", hsl(45, 95, 62))])
vector("ic_w_fog", 48, [cloud(hsl(220, 15, 80), hsl(220, 14, 64), -8), stroke("M10,36H38M14,41H34", hsl(220, 15, 78), 2.4)])
vector("ic_w_snow", 48, [cloud(hsl(210, 30, 92), hsl(220, 16, 70), -6), fill(circle(17, 39, 1.8) + circle(25, 41, 1.8) + circle(33, 39, 1.8), hsl(200, 60, 92))])
MOON = "M30,8a16,16 0 1,0 10,26A13,13 0 0,1 30,8z"
vector("ic_w_moon", 48, [fill(MOON, hsl(48, 70, 82))])
vector("ic_w_partly_night", 48, [
    '    <group android:translateX="-5" android:translateY="-6" android:scaleX="0.8" android:scaleY="0.8">\n' + fill(MOON, hsl(48, 70, 82)) + "\n    </group>",
    '    <group android:translateX="4" android:translateY="4" android:scaleX="0.9" android:scaleY="0.9">\n' + fill(CLOUD, hsl(220, 25, 86), f'android:strokeColor="{cloud_line}" android:strokeWidth="1"') + "\n    </group>",
])

# ---------- 穿著與小圖示（viewport 24，線條） ----------
vector("ic_c_tee", 24, [stroke("M8,4L4,7L6,10L8,9V20H16V9L18,10L20,7L16,4C15.5,5.5 14,6.5 12,6.5S8.5,5.5 8,4z", hsl(40, 80, 75))])
vector("ic_c_long", 24, [stroke("M8,4L4,7V19H7V9M16,4L20,7V19H17V9M8,4C8.5,5.5 10,6.5 12,6.5S15.5,5.5 16,4M7,9V20H17V9", hsl(36, 75, 74))])
vector("ic_c_jacket", 24, [stroke("M8,4L4,7V20H9V9M16,4L20,7V20H15V9M8,4C9,5.6 10.4,6.5 12,6.5S15,5.6 16,4M12,6.5V20", hsl(28, 80, 72))])
vector("ic_c_coat", 24, [stroke("M8,4L4,7V21H9V9M16,4L20,7V21H15V9M8,4L12,9L16,4M12,9V21M10,13H11M10,17H11", hsl(18, 70, 70))])
vector("ic_c_umbrella", 24, [stroke("M3,12A9,9 0 0,1 21,12ZM12,12V18A2,2 0 0,0 16,18", hsl(205, 85, 72))])
vector("ic_c_mask", 24, [stroke("M5,9C8,7 16,7 19,9V14C17,17 7,17 5,14ZM5,10H3V13L5,14M19,10H21V13L19,14M9,11H15M9,13.5H15", hsl(30, 90, 70))])
vector("ic_c_uv", 24, [stroke(circle(12, 12, 4) + "M12,2V4M12,20V22M4,12H2M22,12H20M5,5L6.4,6.4M17.6,17.6L19,19M5,19L6.4,17.6M17.6,6.4L19,5", hsl(45, 90, 70))])
vector("ic_c_wind", 24, [stroke("M3,8H14A3,3 0 1,0 11,5M3,12H18A3,3 0 1,1 15,15M3,16H9", hsl(190, 50, 78))])
vector("ic_w_storm", 24, [stroke(circle(12, 12, 2.5) + "M12,4C7,4 4,7 4,10M12,20C17,20 20,17 20,14M4,10C4,12 5.5,13.5 7,14M20,14C20,12 18.5,10.5 17,10", hsl(18, 90, 72), 2.0)])
vector("ic_notify", 24, [fill(circle(9, 9, 4), "#FFFFFFFF"), fill("M8,20h9a3.5,3.5 0 0,0 0,-7a5,5 0 0,0 -9.5,-1A4,4 0 0,0 8,20z", "#FFFFFFFF")])
# 畫面用的小圖示
vector("ic_play", 24, [stroke("M11,5L6,9H3V15H6L11,19ZM15.5,8.5A5,5 0 0,1 15.5,15.5M18.5,5.5A9,9 0 0,1 18.5,18.5", hsl(198, 70, 80))])
vector("ic_gear", 24, [stroke(circle(12, 12, 3) + "M12,2.5V5M12,19V21.5M2.5,12H5M19,12H21.5M5.3,5.3L7.1,7.1M16.9,16.9L18.7,18.7M5.3,18.7L7.1,16.9M16.9,7.1L18.7,5.3", hsl(220, 20, 80))])
vector("ic_refresh", 24, [stroke("M20,11A8,8 0 1,0 18,17M20,5V11H14", hsl(220, 20, 80))])

# ---------- 小工具背景（shape） ----------
def shape(name, radius, solid=None, strokeC=None, grad=None):
    parts = []
    if grad:
        parts.append(f'    <gradient android:angle="{grad[2]}" android:startColor="{grad[0]}" android:endColor="{grad[1]}" />')
    if solid:
        parts.append(f'    <solid android:color="{solid}" />')
    if strokeC:
        parts.append(f'    <stroke android:width="1dp" android:color="{strokeC}" />')
    parts.append(f'    <corners android:radius="{radius}dp" />')
    write(name, '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n' + "\n".join(parts) + "\n</shape>")

shape("bg_scrim", 22, solid=hsl(230, 30, 8, .28))
shape("bg_panel_c", 24, strokeC=hsl(220, 40, 40, .35), grad=(hsl(212, 40, 18, .95), hsl(250, 28, 16, .95), 315))
shape("bg_block", 17, solid=hsl(220, 30, 10, .45))
shape("bg_block_tomorrow", 17, solid=hsl(35, 50, 40, .16), strokeC=hsl(35, 70, 65, .22))
shape("bg_switch", 13, solid=hsl(230, 30, 12, .35), strokeC=hsl(220, 40, 85, .22))
shape("bg_glass", 22, solid=hsl(230, 30, 14, .55), strokeC=hsl(220, 40, 80, .12))
shape("bg_badge", 99, solid=hsl(14, 70, 45, .35), strokeC=hsl(14, 80, 62, .55))
shape("ic_dot_on", 3, solid=hsl(198, 80, 75))
shape("ic_dot_off", 3, solid=hsl(220, 30, 85, .45))
print("ok", len(os.listdir(OUT)))
