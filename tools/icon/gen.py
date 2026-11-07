import cairosvg, io, numpy as np
from PIL import Image
PH="M8.2 3.8c.5 0 .9.3 1.1.7l1.3 3c.2.5.1 1-.3 1.4l-1.4 1.3c1 2.1 2.7 3.8 4.8 4.8l1.3-1.4c.4-.4.9-.5 1.4-.3l3 1.3c.4.2.7.6.7 1.1v2.4c0 .9-.7 1.6-1.6 1.6C10.4 19.7 4.3 13.6 4.3 5.4c0-.9.7-1.6 1.6-1.6z"
PC=(11.3785,12.5823)
THEMES={
 'light':dict(bg=('#FFFFFF','#EDF1F7'),top=('#101A2E','#2C4A80'),bot=('#50A0FF','#1A6FE0')),
 'dark':dict(bg=('#1F2738','#141923'),top=('#F4F6FA','#B7CBEA'),bot=('#9CCBFF','#4D9BFF')),
 'tinted':dict(bg=None,top=('#FFFFFF','#FFFFFF'),bot=('#D2D2D2','#9A9A9A')),
 'mono':dict(bg=None,top=('#000','#000'),bot=('#000','#000')),
}
def tf(cx,cy,K,dx=0): return f'translate({cx+dx:.3f} {cy:.3f}) scale({K:.4f}) rotate(12) translate({-PC[0]} {-PC[1]})'
def mark(p,th='light',pre='a'):
    """p: K, w (dark stroke, path units), cx, cy, wl (water y, dp), shift (dp)"""
    t=THEMES[th]; K,w,cx,cy,wl,sh=p['K'],p['w'],p['cx'],p['cy'],p['wl'],p['shift']
    top_y0=cy-11*K; bot_y1=cy+11*K
    s=f'''<defs><clipPath id="{pre}t"><rect x="-50" y="-50" width="210" height="{wl+50}"/></clipPath>
<clipPath id="{pre}b"><rect x="-50" y="{wl}" width="210" height="200"/></clipPath>
<linearGradient id="{pre}gt" gradientUnits="userSpaceOnUse" x1="0" y1="{top_y0:.2f}" x2="0" y2="{wl}"><stop offset="0" stop-color="{t['top'][0]}"/><stop offset="1" stop-color="{t['top'][1]}"/></linearGradient>
<linearGradient id="{pre}gb" gradientUnits="userSpaceOnUse" x1="0" y1="{wl}" x2="0" y2="{bot_y1:.2f}"><stop offset="0" stop-color="{t['bot'][0]}"/><stop offset="1" stop-color="{t['bot'][1]}"/></linearGradient></defs>
<g clip-path="url(#{pre}t)"><path d="{PH}" transform="{tf(cx,cy,K)}" fill="none" stroke="url(#{pre}gt)" stroke-width="{w:.3f}" stroke-linejoin="round"/></g>
<g clip-path="url(#{pre}b)"><path d="{PH}" transform="{tf(cx,cy,K,sh)}" fill="none" stroke="url(#{pre}gb)" stroke-width="{w*p.get('blue',1.1):.3f}" stroke-linejoin="round"/></g>'''
    return s
def tile(th):
    t=THEMES[th]
    if not t['bg']: return ''
    return f'<defs><linearGradient id="bg{th}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t["bg"][0]}"/><stop offset="1" stop-color="{t["bg"][1]}"/></linearGradient></defs><rect width="108" height="108" fill="url(#bg{th})"/>'
def svg(body,vb="0 0 108 108"): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{body}</svg>'
def png(s,size=432): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=s.encode(),output_width=size,output_height=size))).convert('RGBA')
def ink(s,size=432):
    a=np.array(png(s,size))[:,:,3].astype(float)/255; return a
def stats(a):
    n=a.shape[0]; ys,xs=np.mgrid[0:n,0:n]; m=a.sum()
    cy=(ys*a).sum()/m; cx=(xs*a).sum()/m
    nz=np.argwhere(a>.3); (y0,x0),(y1,x1)=nz.min(0),nz.max(0)
    f=108/n
    return dict(area=m*f*f, cx=cx*f, cy=cy*f, bbox=(x0*f,y0*f,x1*f,y1*f))
# Mail mark for reference (from app#242)
def mail(th='mono'):
    t=THEMES[th]
    return f'''<g transform="translate(26.029 30.824) scale(.999)"><defs><clipPath id="mt"><rect x="-10" y="-10" width="76" height="37"/></clipPath><clipPath id="mb"><rect x="-10" y="27" width="76" height="39"/></clipPath>
<linearGradient id="mgt" gradientUnits="userSpaceOnUse" x1="0" y1="4.9" x2="0" y2="27"><stop offset="0" stop-color="{t['top'][0]}"/><stop offset="1" stop-color="{t['top'][1]}"/></linearGradient>
<linearGradient id="mgb" gradientUnits="userSpaceOnUse" x1="0" y1="27" x2="0" y2="43.6"><stop offset="0" stop-color="{t['bot'][0]}"/><stop offset="1" stop-color="{t['bot'][1]}"/></linearGradient></defs>
<circle cx="24" cy="24" r="14.6" clip-path="url(#mt)" fill="none" stroke="url(#mgt)" stroke-width="9"/>
<circle cx="32" cy="24" r="14.6" clip-path="url(#mb)" fill="none" stroke="url(#mgb)" stroke-width="10"/></g>'''
