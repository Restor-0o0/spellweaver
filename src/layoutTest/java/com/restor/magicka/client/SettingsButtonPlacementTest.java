package com.restor.magicka.client;

import java.util.*;
import com.restor.magicka.client.SettingsButtonPlacement.Rect;
import com.restor.magicka.client.SettingsButtonPlacement.Point;

public final class SettingsButtonPlacementTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static boolean clear(int x,int y,List<Rect> obstacles){
        for(var rect:obstacles){int horizontal=Math.max(rect.x()-x-20,x-rect.x()-rect.width()),vertical=Math.max(rect.y()-y-20,y-rect.y()-rect.height());if(horizontal<4&&vertical<4)return false;}return true;
    }
    public static void main(String[] args){
        var anchor=new Rect(100,80,100,20);var left=new Rect(76,80,20,20);var right=new Rect(204,80,20,20);
        check(new Point(76,80).equals(SettingsButtonPlacement.find(320,200,anchor,20,20,List.of(anchor))),"Prefer the usual left-hand position");
        check(new Point(204,80).equals(SettingsButtonPlacement.find(320,200,anchor,20,20,List.of(anchor,left))),"Avoid another mod's left-hand button");
        var both=List.of(anchor,left,right);var top=SettingsButtonPlacement.find(320,200,anchor,20,20,both);check(top!=null&&clear(top.x(),top.y(),both),"Find another position when both sides are occupied");
        check(SettingsButtonPlacement.find(320,200,anchor,20,20,List.of(new Rect(0,0,320,200)))==null,"Hide when no free position exists");
        check(SettingsButtonPlacement.find(26,200,anchor,20,20,List.of())==null,"Hide in a viewport smaller than the button and margins");
        var layout=new SettingsButtonPlacement();var initial=layout.place(320,200,anchor,List.of(anchor));check(layout.place(320,200,anchor,List.of(anchor))==initial,"Reuse an unchanged layout");
        var moved=layout.place(320,200,anchor,List.of(anchor,left));check(!moved.equals(initial)&&clear(moved.x(),moved.y(),List.of(anchor,left)),"React to widgets added after initialization");
        check(layout.place(320,200,anchor,List.of(anchor))==moved,"Keep a safe position when unrelated widgets change");
        check(layout.place(320,200,anchor,List.of(new Rect(0,0,320,200)))==null,"React to a full-screen obstruction");
        check(layout.place(320,200,anchor,List.of(anchor))!=null,"Restore the shortcut when space becomes free");
        var random=new Random(34567);
        for(int sample=0;sample<2000;sample++){
            int width=24+random.nextInt(61),height=24+random.nextInt(49);var subject=new Rect(random.nextInt(width),random.nextInt(height),5+random.nextInt(24),5+random.nextInt(16));var obstacles=new ArrayList<Rect>();obstacles.add(subject);
            for(int i=0,count=random.nextInt(9);i<count;i++)obstacles.add(new Rect(random.nextInt(width+20)-10,random.nextInt(height+20)-10,1+random.nextInt(30),1+random.nextInt(25)));
            var point=SettingsButtonPlacement.find(width,height,subject,20,20,obstacles);
            if(point!=null)check(point.x()>=4&&point.y()>=4&&point.x()+20<=width-4&&point.y()+20<=height-4&&clear(point.x(),point.y(),obstacles),"No clipping or overlapping, sample "+sample);
            else{boolean available=false;for(int y=4;y<=height-24&&!available;y++)for(int x=4;x<=width-24;x++)if(clear(x,y,obstacles)){available=true;break;}check(!available,"Do not hide when a free position exists, sample "+sample);}
        }
        System.out.println("SETTINGS_BUTTON_PLACEMENT_OK: collision scenarios, live updates and 2000 randomized viewports");
    }
}
