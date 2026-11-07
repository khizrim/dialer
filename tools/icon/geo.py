import numpy as np, svgpathtools as spt
from shapely.geometry import Polygon, box
from shapely import affinity
from gen import PH, PC, THEMES, tile, svg, png, ink, stats, mail
P=spt.parse_path(PH)
pts=[]
for seg in P:
    n=max(4,int(seg.length()*12))
    for t in np.linspace(0,1,n,endpoint=False): z=seg.point(t); pts.append((z.real,z.imag))
SHAPE=Polygon(pts).buffer(0)
def band(d,w):
    outer=SHAPE.buffer(d+w/2,quad_segs=24,join_style='round')
    inner=SHAPE.buffer(d-w/2,quad_segs=24,join_style='round')
    return outer.difference(inner)
def place(g,K,cx,cy,dx=0):
    g=affinity.translate(g,-PC[0],-PC[1]); g=affinity.rotate(g,12,origin=(0,0))
    g=affinity.scale(g,K,K,origin=(0,0)); return affinity.translate(g,cx+dx,cy)
def parts(p):
    b=band(p['d'],p['w']); bb=band(p['d'],p['w']*p.get('blue',1.1))
    top=place(b,p['K'],p['cx'],p['cy']).intersection(box(-50,-50,160,p['wl']))
    bot=place(bb,p['K'],p['cx'],p['cy'],p['shift']).intersection(box(-50,p['wl'],160,160))
    return clean(top),clean(bot)
def clean(g):
    gs=[x for x in getattr(g,'geoms',[g]) if x.geom_type=='Polygon']
    big=max(x.area for x in gs)
    from shapely.ops import unary_union
    return unary_union([x for x in gs if x.area>.12*big])
def topath(g):
    polys=getattr(g,'geoms',[g]); s=''
    for pg in polys:
        if pg.is_empty or pg.geom_type!='Polygon': continue
        for ring in [pg.exterior,*pg.interiors]:
            c=list(ring.coords)[:-1]
            s+='M'+' '.join(f'{x:.2f},{y:.2f}' for x,y in c)+'Z'
    return s
def mark(p,th='light',pre='a'):
    t=THEMES[th]; top,bot=parts(p)
    ty0=top.bounds[1]; by1=bot.bounds[3]; wl=p['wl']
    return f'''<defs><linearGradient id="{pre}gt" gradientUnits="userSpaceOnUse" x1="0" y1="{ty0:.2f}" x2="0" y2="{wl:.2f}"><stop offset="0" stop-color="{t['top'][0]}"/><stop offset="1" stop-color="{t['top'][1]}"/></linearGradient>
<linearGradient id="{pre}gb" gradientUnits="userSpaceOnUse" x1="0" y1="{wl:.2f}" x2="0" y2="{by1:.2f}"><stop offset="0" stop-color="{t['bot'][0]}"/><stop offset="1" stop-color="{t['bot'][1]}"/></linearGradient></defs>
<path fill-rule="evenodd" d="{topath(top)}" fill="url(#{pre}gt)"/><path fill-rule="evenodd" d="{topath(bot)}" fill="url(#{pre}gb)"/>'''
CY=55.2
def build(K,w,d,wlf=.5,shf=3.0,blue=1.1,cy=CY):
    p=dict(K=K,w=w,d=d,blue=blue,cx=54,cy=54,wl=54,shift=shf*K)
    for _ in range(5):
        b=place(band(d,w),K,p['cx'],p['cy']).bounds
        p['wl']=b[1]+wlf*(b[3]-b[1])
        st=stats(ink(svg(mark(p,'mono'))))
        p['cx']+=54-st['cx']; p['cy']+=cy-st['cy']
    return p,stats(ink(svg(mark(p,'mono'))))
