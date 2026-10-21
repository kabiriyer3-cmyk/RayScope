#!/usr/bin/env python3
"""Draws the RayScope app icon (dark squircle, cyan scope ring, power glyph).

Usage: make_icon.py OUT.ico [OUT.png]
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter

S=1024
def render():
    img=Image.new("RGBA",(S,S),(0,0,0,0))
    # squircle background with vertical gradient
    bg=Image.new("RGBA",(S,S))
    d=ImageDraw.Draw(bg)
    for y in range(S):
        t=y/S
        c=(int(20+(10-20)*t),int(26+(13-26)*t),int(36+(18-36)*t),255)
        d.line([(0,y),(S,y)],fill=c)
    mask=Image.new("L",(S,S),0)
    ImageDraw.Draw(mask).rounded_rectangle([40,40,S-40,S-40],radius=230,fill=255)
    img.paste(bg,(0,0),mask)
    # glow
    glow=Image.new("RGBA",(S,S),(0,0,0,0))
    ImageDraw.Draw(glow).ellipse([262,262,762,762],fill=(56,225,255,90))
    glow=glow.filter(ImageFilter.GaussianBlur(90))
    img=Image.alpha_composite(img,Image.composite(glow,Image.new("RGBA",(S,S),(0,0,0,0)),mask))
    ov=Image.new("RGBA",(S,S),(0,0,0,0))
    d=ImageDraw.Draw(ov)
    cx=cy=S/2
    # ticks ring
    for i in range(48):
        a=math.radians(i*7.5-90)
        long=i%4==0
        r2=360; r1=r2-(56 if long else 34)
        k=max(0.0,1-((i*7.5)%360)/250)
        col=(56,225,255,int(70+185*k))
        d.line([(cx+math.cos(a)*r1,cy+math.sin(a)*r1),(cx+math.cos(a)*r2,cy+math.sin(a)*r2)],fill=col,width=18 if long else 12)
    # inner disc
    d.ellipse([cx-215,cy-215,cx+215,cy+215],fill=(56,225,255,30),outline=(56,225,255,140),width=8)
    # power glyph
    w=46
    r=128
    d.arc([cx-r,cy-r+18,cx+r,cy+r+18],start=-55,end=235,fill=(56,225,255,255),width=w)
    d.line([(cx,cy-150),(cx,cy-10)],fill=(56,225,255,255),width=w)
    d.ellipse([cx-w/2,cy-150-w/2,cx+w/2,cy-150+w/2],fill=(56,225,255,255))
    d.ellipse([cx-w/2,cy-10-w/2,cx+w/2,cy-10+w/2],fill=(56,225,255,255))
    img=Image.alpha_composite(img,ov)
    # glass top highlight
    hl=Image.new("RGBA",(S,S),(0,0,0,0))
    hd=ImageDraw.Draw(hl)
    for y in range(40,S//2):
        a=int(38*(1-(y-40)/(S//2-40)))
        hd.line([(0,y),(S,y)],fill=(255,255,255,a))
    rim=Image.new("L",(S,S),0)
    ImageDraw.Draw(rim).rounded_rectangle([40,40,S-40,S-40],radius=230,outline=255,width=6)
    img=Image.alpha_composite(img,Image.composite(hl,Image.new("RGBA",(S,S),(0,0,0,0)),mask))
    rimc=Image.new("RGBA",(S,S),(255,255,255,70))
    img=Image.alpha_composite(img,Image.composite(rimc,Image.new("RGBA",(S,S),(0,0,0,0)),rim))
    return img


def main():
    if len(sys.argv) < 2:
        print("usage: make_icon.py OUT.ico [OUT.png]")
        sys.exit(2)
    im = render()
    lanczos = getattr(getattr(Image, "Resampling", Image), "LANCZOS")
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    im.resize((256, 256), lanczos).save(sys.argv[1], sizes=sizes)
    if len(sys.argv) > 2:
        im.resize((512, 512), lanczos).save(sys.argv[2])
    print("icon written:", sys.argv[1])


if __name__ == "__main__":
    main()
