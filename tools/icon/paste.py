"""Paste an adaptive icon (108dp SVG) onto a real Samsung home screenshot in place of a tile.
usage: paste(shot, target_bbox, ref_bbox, svg_str) -> PIL image"""
import numpy as np
from PIL import Image
from gen import png
def paste(shot, tgt, ref, s108, logo_r=.36):
    im=np.array(shot.convert('RGB')).astype(float)
    x0,y0,x1,y1=ref; R=im[y0:y1+1,x0:x1+1].copy(); h,w=R.shape[:2]
    mask=(R.sum(2)>60).astype(float)
    yy,xx=np.mgrid[0:h,0:w]; r=np.hypot(yy-h/2,xx-w/2)/w
    ringpx=R[(r>logo_r+.03)&(r<logo_r+.07)&(mask>0)]
    face=np.median(ringpx,0)
    blank=R.copy(); blank[r<logo_r+.04]=face
    shade=blank.mean(2,keepdims=True)/face.mean()
    S=int(round(w*1.5)); ic=np.array(png(s108,S).convert('RGB')).astype(float)
    o=(S-w)//2; ic=ic[o:o+h,o:o+w]
    out=np.clip(ic*shade,0,255)
    tx0,ty0=tgt[0],tgt[1]
    T=im[ty0:ty0+h,tx0:tx0+w]
    m=mask[...,None]
    im[ty0:ty0+h,tx0:tx0+w]=out*m+np.zeros_like(T)*(1-m)
    return Image.fromarray(im.astype(np.uint8))
