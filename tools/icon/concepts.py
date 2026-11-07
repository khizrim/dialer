import numpy as np, json
from shapely.geometry import Point, LineString, Polygon, box
from shapely.ops import unary_union
from shapely import affinity
from gen import THEMES, tile, svg, png, ink, stats, mail
import geo
def arc(c,r,a0,a1,n=80):  # angles in degrees, screen coords (y down), 0 = right, 90 = down
    t=np.radians(np.linspace(a0,a1,n)); return [(c[0]+r*np.cos(x),c[1]+r*np.sin(x)) for x in t]
def stroke(pts,w,closed=False):
    if closed: return Polygon(pts).buffer(w/2,quad_segs=24).difference(Polygon(pts).buffer(-w/2,quad_segs=24))
    return LineString(pts).buffer(w/2,quad_segs=24,cap_style='round',join_style='round')
W=8.4
def bubble():
    r=14.6; ro=r+W/2
    ring=Point(0,0).buffer(ro,quad_segs=32).difference(Point(0,0).buffer(r-W/2,quad_segs=32))
    a=lambda d:(ro*np.cos(np.radians(d)),ro*np.sin(np.radians(d)))
    tail=Polygon([a(105),a(150),(-ro*.92,ro*1.02)]).buffer(1.6,quad_segs=12).buffer(-1.0,quad_segs=12)
    return unary_union([ring.buffer(-.01),tail.difference(Point(0,0).buffer(r-W/2))])
def archandset():
    c=(0,0); r=13
    a=arc(c,r,100,260)                         # left half
    e1=a[0]; e2=a[-1]
    cap=lambda p,ang: LineString([p,(p[0]+5.5*np.cos(np.radians(ang)),p[1]+5*np.sin(np.radians(ang)))]).buffer(W*.62,quad_segs=24,cap_style='round')
    g=unary_union([stroke(a,W),cap(e1,100+180-40),cap(e2,260+180+40)])
    return affinity.rotate(g,-40,origin=(0,0))
def ripples():
    r=15.5
    ring=Point(0,0).buffer(r+W/2,quad_segs=32).difference(Point(0,0).buffer(r-W/2,quad_segs=32))
    return unary_union([ring,Point(0,0).buffer(4.8,quad_segs=24)])
SHAPES={'bubble':(bubble,.55),'arc':(archandset,.5),'ripples':(ripples,None)}
def parts(G,p):
    g=affinity.scale(G,p['k'],p['k'],origin=(0,0)); g=affinity.translate(g,p['cx'],p['cy'])
    gb=g.buffer(p['k']*W*.05)   # blue +10%
    top=g.intersection(box(-50,-50,160,p['wl'])); bot=affinity.translate(gb,p['shift'],0).intersection(box(-50,p['wl'],160,160))
    return geo.clean(top) if not top.is_empty else top, bot
def mark(G,p,th='light',pre='x'):
    t=THEMES[th]; top,bot=parts(G,p); wl=p['wl']
    return f'''<defs><linearGradient id="{pre}gt" gradientUnits="userSpaceOnUse" x1="0" y1="{top.bounds[1]:.2f}" x2="0" y2="{wl:.2f}"><stop offset="0" stop-color="{t['top'][0]}"/><stop offset="1" stop-color="{t['top'][1]}"/></linearGradient>
<linearGradient id="{pre}gb" gradientUnits="userSpaceOnUse" x1="0" y1="{wl:.2f}" x2="0" y2="{bot.bounds[3]:.2f}"><stop offset="0" stop-color="{t['bot'][0]}"/><stop offset="1" stop-color="{t['bot'][1]}"/></linearGradient></defs>
<path fill-rule="evenodd" d="{geo.topath(top)}" fill="url(#{pre}gt)"/><path fill-rule="evenodd" d="{geo.topath(bot)}" fill="url(#{pre}gb)"/>'''
def fit(name,target=850,wlf=.55,shiftf=.27,ripple=False):
    G=SHAPES[name][0]()
    k=1.0
    for _ in range(8):
        p=dict(k=k,cx=54,cy=54,wl=54,shift=0)
        for _ in range(4):
            b=affinity.translate(affinity.scale(G,k,k,origin=(0,0)),p['cx'],p['cy']).bounds
            p['wl']=(p['cy']+ .0) if ripple else b[1]+wlf*(b[3]-b[1])
            p['shift']=shiftf*(b[2]-b[0])
            st=stats(ink(svg(mark(G,p,'mono'))))
            p['cx']+=54-st['cx']; p['cy']+=55.2-st['cy']
        k*= (target/st['area'])**.5
    return G,p,st
def keypad():
    g=[Point(x*12,y*12).buffer(4.3,quad_segs=24) for x in (-1,0,1) for y in (-1,0,1)]
    return unary_union(g)
def waves():
    dot=Point(-10,0).buffer(4.6,quad_segs=24)
    a1=stroke(arc((-10,0),11,-55,55),W*.9); a2=stroke(arc((-10,0),21,-55,55),W*.9)
    return unary_union([dot,a1,a2])
def tworings():
    r=11.5
    a=Point(-7.5,0).buffer(r+W*.42,quad_segs=32).difference(Point(-7.5,0).buffer(r-W*.42,quad_segs=32))
    b=Point(7.5,0).buffer(r+W*.42,quad_segs=32).difference(Point(7.5,0).buffer(r-W*.42,quad_segs=32))
    return unary_union([a,b])
def parens():
    l=stroke(arc((6,0),17,130,230),W); r=stroke(arc((-6,0),17,-50,50),W)
    return unary_union([l,r])
SHAPES.update({'keypad':(keypad,None),'waves':(waves,None),'tworings':(tworings,None),'parens':(parens,None)})
