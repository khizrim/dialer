import json
from shapely import affinity
from gen import THEMES, tile, svg, png, ink, stats, mail
import geo, concepts as C
def handsetG(d=1.2,w=7.4/1.72):
    return geo.place(geo.band(d,w),1.0,0,0)
SH={'handset':handsetG,'bubble':C.bubble,'ripples':C.ripples,'keypad':C.keypad,'waves':C.waves}
def body(G,p,mode,pre):
    g=affinity.translate(affinity.scale(G,p['k'],p['k'],origin=(0,0)),p['cx'],p['cy'])
    y0,y1=g.bounds[1],g.bounds[3]; wl=y0+p['wlf']*(y1-y0)
    f=lambda y:(y-y0)/(y1-y0)
    if mode=='hard':
        stops=[(0,'#101A2E'),(f(wl),'#2C4A80'),(f(wl)+.001,'#50A0FF'),(1,'#1A6FE0')]
    elif mode=='soft':
        stops=[(0,'#101A2E'),(f(wl)-.12,'#2C4A80'),(f(wl)+.10,'#50A0FF'),(1,'#1A6FE0')]
    else:  # blue: all water, light from the top
        stops=[(0,'#50A0FF'),(1,'#1A6FE0')]
    st=''.join(f'<stop offset="{max(0,min(1,o)):.3f}" stop-color="{c}"/>' for o,c in stops)
    return f'<defs><linearGradient id="{pre}" gradientUnits="userSpaceOnUse" x1="0" y1="{y0:.2f}" x2="0" y2="{y1:.2f}">{st}</linearGradient></defs><path fill-rule="evenodd" fill="url(#{pre})" d="{geo.topath(g)}"/>'
def fit(name,target=850,wlf=.5):
    G=SH[name](); k=1.0; p=dict(k=k,cx=54,cy=54,wlf=wlf)
    for _ in range(6):
        for _ in range(3):
            s=stats(ink(svg(body(G,p,'blue','m'))))
            p['cx']+=54-s['cx']; p['cy']+=55.2-s['cy']
        p['k']*=(target/s['area'])**.5
    return G,p
