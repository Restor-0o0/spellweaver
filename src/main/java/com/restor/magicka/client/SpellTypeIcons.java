package com.restor.magicka.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class SpellTypeIcons {
    private static final int[] COLORS={0xFFB49B,0xA4F5B3,0xB9DFFF,0xE7D1AF,0xFFBF78,0x9DDC80,0xF2CF83,0xFFE18B,0xF2CAA0};
    private static final String[][] GLYPHS={
        {"..........sss",".........ssss","........ssss.",".......ssss..","......ssss...","..m..ssss....","...mssss.....","....msm......","...wwmmm.....","..ww...m.....",".ww..........","ww..........."},
        {"....ggggg....","....ghhhg....","....ghhhg....","....ghhhg....","ggggghhhggggg","ghhhhhhhhhhhg","ghhhhhhhhhhhg","ggggghhhggggg","....ghhhg....","....ghhhg....","....ghhhg....","....ggggg...."},
        {".ssss........","ss..ss.......","s....s.......","s....s.......","ss..sms......",".ssmmmss.....","....sms..ss..",".....s....s..",".....s....s..",".....ss..ss..","......ssss..."},
        {"rrr..........","rbb..........",".............","rrr.rrr......","rbb.rbb......",".............","r.rrr.rrr.r..","b.rbb.rbb.b..",".............","rrr.rrr.rrr..","rbb.rbb.rbb.."},
        {"hhhhh.hhhhh..","ccccc.ccccc..","cccc..ccccc..","cccc.cccccc..","ccc..cccccc..","cccc..ccccc..","ccccc..cccc..","cccc..ccccc..","cccc.cccccc..","ccc..cccccc..","ccc.ccccccc.."},
        {"ggggg...ggggg","gegeg...gegeg","ggggg...ggggg",".ggg.....ggg.","gtttgg.gtttgg","gtttg..gtttg.",".ttt....ttt..",".ppp....ppp..",".p.p....p.p..",".p.p....p.p..",".eee....eee.."},
        {"....mmm....","...m...m...","...mmmmm...","..mmmmmmm..",".m.yyyyy.m.",".m.yyoyy.m.",".m.yoooy.m.",".m.yyyyy.m.","..mmmmmmm..",".mmmmmmmmm.","..mmmmmmm.."},
        {".mmmmmmmmm...",".mtttttttm...",".mtsshsttm...",".mtsyssttm...",".mtsyssttm...",".mtsyssttm...",".mttsstttm...","..mtttttm....","...mtttm.....","....mtm......",".....m......."},
        {"..sssssss..",".ssmmwmmss.",".s...w...s.",".....w.....",".....w.....",".....w.....",".....w.....",".....w.....",".....w.....",".....w.....",".....w....."}
    };
    static void draw(GuiGraphicsExtractor g,int type,int x,int y,int width,int height){
        String[] glyph=GLYPHS[type];
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=0,maxY=0;
        for(int row=0;row<glyph.length;row++)for(int col=0;col<glyph[row].length();col++)if(glyph[row].charAt(col)!='.'){
            minX=Math.min(minX,col);maxX=Math.max(maxX,col);minY=Math.min(minY,row);maxY=Math.max(maxY,row);
        }
        int left=x+(width-(maxX-minX+1))/2-minX,top=y+(height-(maxY-minY+1))/2-minY;
        g.pose().pushMatrix();g.pose().translate((float)(left),(float)(top));
        for(int row=minY;row<=maxY;row++)for(int col=minX;col<=maxX;col++)if(glyph[row].charAt(col)!='.')g.fill(col-1,row-1,col+2,row+2,HudView.color(0xFF15111D));
        for(int row=minY;row<=maxY;row++)for(int col=minX;col<=maxX;col++)if(glyph[row].charAt(col)!='.')g.fill(col,row,col+1,row+1,HudView.color(pixelColor(type,glyph[row].charAt(col))));
        g.pose().popMatrix();
    }
    private static int pixelColor(int type,char pixel){
        return 0xFF000000|switch(pixel){
            case 'h'->0xEDE5CD;
            case 'r'->0xD39172;
            case 'b'->0xA56854;
            case 'c'->0x929DA8;
            case 'g'->0x87B66B;
            case 'e'->0x283528;
            case 't'->0x579A9A;
            case 'p'->0x7775AA;
            case 'm'->0xB89561;
            case 'y'->0xF3DB91;
            case 'o'->0xE89A47;
            case 's'->0xC4D4D9;
            case 'w'->0xB38A59;
            default->COLORS[type];
        };
    }
    private SpellTypeIcons(){}
}
