package com.restor.magicka.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiText {
    private record BlockKey(Component text,float width,float height,float maxScale){}
    private record BlockLayout(List<FormattedCharSequence> lines,float scale,int advance,float height){}
    private static final Map<BlockKey,BlockLayout> BLOCKS=new LinkedHashMap<>();
    private static Object cachedFont,cachedLanguage;
    private static long cachedAt;
    public static float line(GuiGraphicsExtractor g,Component text,float x,float y,float width,float height,float maxScale,boolean centered,int color){
        return line(g,text.getVisualOrderText(),x,y,width,height,maxScale,centered,color);
    }
    public static float line(GuiGraphicsExtractor g,FormattedCharSequence text,float x,float y,float width,float height,float maxScale,boolean centered,int color){
        if(width<=0 || height<=0 || maxScale<=0)return 0;
        var font=Minecraft.getInstance().font;
        int textWidth=font.width(text);
        float scale=Math.min(maxScale,Math.min(width/Math.max(1,textWidth),height/font.lineHeight));
        float drawnWidth=textWidth*scale;
        g.pose().pushMatrix();g.pose().translate(x+(centered?(width-drawnWidth)/2:0),y+(height-font.lineHeight*scale)/2);g.pose().scale(scale,scale);
        g.text(font,text,0,0,HudView.color(color),false);g.pose().popMatrix();
        return drawnWidth;
    }
    public static float block(GuiGraphicsExtractor g,Component text,float x,float y,float width,float height,float maxScale,boolean centered,boolean middle,int color){
        if(width<=0 || height<=0 || maxScale<=0 || text.getString().isEmpty())return 0;
        return drawBlock(g,text,x,y,width,height,maxScale,centered,middle,color);
    }
    private static BlockLayout layout(Font font,Component text,float width,float height,float maxScale){
        long now=System.nanoTime();Object language=net.minecraft.locale.Language.getInstance();
        if(cachedFont!=font || cachedLanguage!=language || now-cachedAt>100_000_000L){BLOCKS.clear();cachedFont=font;cachedLanguage=language;cachedAt=now;}
        var key=new BlockKey(text.copy(),width,height,maxScale);
        var cached=BLOCKS.get(key);if(cached!=null)return cached;
        int advance=font.lineHeight+1;
        float scale=maxScale;
        var lines=font.split(text,Math.max(1,(int)(width/scale)));
        if(!fits(font,lines,scale,width,height,advance)){
            float low=0,high=maxScale;
            for(int step=0;step<12;step++){
                float candidate=(low+high)/2;
                var wrapped=font.split(text,Math.max(1,(int)(width/candidate)));
                if(fits(font,wrapped,candidate,width,height,advance))low=candidate;else high=candidate;
            }
            scale=low>0?low:Math.min(maxScale,Math.min(width/Math.max(1,font.width(text)),height/((text.getString().length()+1f)*advance)));
            lines=font.split(text,Math.max(1,(int)(width/scale)));
        }
        float drawnHeight=(Math.max(0,lines.size()-1)*advance+font.lineHeight)*scale;
        var result=new BlockLayout(List.copyOf(lines),scale,advance,drawnHeight);
        if(BLOCKS.size()>=128)BLOCKS.clear();BLOCKS.put(key,result);return result;
    }
    private static boolean fits(Font font,List<FormattedCharSequence> lines,float scale,float width,float height,int advance){
        return (Math.max(0,lines.size()-1)*advance+font.lineHeight)*scale<=height && lines.stream().allMatch(line->font.width(line)*scale<=width);
    }
    private static float drawBlock(GuiGraphicsExtractor g,Component text,float x,float y,float width,float height,float maxScale,boolean centered,boolean middle,int color){
        var font=Minecraft.getInstance().font;
        var layout=layout(font,text,width,height,maxScale);var lines=layout.lines();float scale=layout.scale(),drawnHeight=layout.height();int advance=layout.advance();
        g.pose().pushMatrix();g.pose().translate(x,y+(middle?(height-drawnHeight)/2:0));g.pose().scale(scale,scale);
        for(int i=0;i<lines.size();i++){
            var line=lines.get(i);
            int left=centered?Math.round((width/scale-font.width(line))/2):0;
            g.text(font,line,left,i*advance,HudView.color(color),false);
        }
        g.pose().popMatrix();return drawnHeight;
    }
    private UiText(){}
}
