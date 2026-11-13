package com.restor.magicka.client;

import java.util.*;

public final class SettingsButtonPlacement {
    public record Rect(int x,int y,int width,int height){}
    public record Point(int x,int y){}
    private static final int GAP=4;
    private int previousWidth=-1,previousHeight=-1;
    private Rect previousAnchor;
    private List<Rect> previousObstacles=List.of();
    private Point previousPoint;
    public Point place(int width,int height,Rect anchor,List<Rect> obstacles){
        boolean sameAnchor=width==previousWidth&&height==previousHeight&&anchor.equals(previousAnchor);
        if(sameAnchor&&obstacles.equals(previousObstacles))return previousPoint;
        Point retained=sameAnchor&&previousPoint!=null&&free(previousPoint,width,height,20,20,obstacles)?previousPoint:null;
        previousWidth=width;previousHeight=height;previousAnchor=anchor;previousObstacles=List.copyOf(obstacles);
        previousPoint=retained!=null?retained:find(width,height,anchor,20,20,obstacles);return previousPoint;
    }
    public static Point find(int width,int height,Rect anchor,int sizeX,int sizeY,List<Rect> obstacles){
        if(width<sizeX+GAP*2||height<sizeY+GAP*2)return null;
        int centerX=anchor.x+(anchor.width-sizeX)/2,centerY=anchor.y+(anchor.height-sizeY)/2;
        var preferred=List.of(new Point(anchor.x-sizeX-GAP,centerY),new Point(anchor.x+anchor.width+GAP,centerY),new Point(centerX,anchor.y-sizeY-GAP),new Point(centerX,anchor.y+anchor.height+GAP));
        for(var point:preferred)if(free(point,width,height,sizeX,sizeY,obstacles))return point;
        Set<Integer> xs=new TreeSet<>(),ys=new TreeSet<>();xs.add(GAP);xs.add(width-sizeX-GAP);ys.add(GAP);ys.add(height-sizeY-GAP);
        for(var point:preferred){xs.add(point.x);ys.add(point.y);}
        for(var rect:obstacles){xs.add(rect.x-sizeX-GAP);xs.add(rect.x+rect.width+GAP);ys.add(rect.y-sizeY-GAP);ys.add(rect.y+rect.height+GAP);}
        Point best=null;long score=Long.MAX_VALUE;
        for(int y:ys)for(int x:xs){
            if(x<GAP||y<GAP||x+sizeX>width-GAP||y+sizeY>height-GAP)continue;
            long dx=2L*x+sizeX-(2L*anchor.x+anchor.width),dy=2L*y+sizeY-(2L*anchor.y+anchor.height),distance=dx*dx+dy*dy;
            if(distance>=score)continue;var point=new Point(x,y);if(free(point,width,height,sizeX,sizeY,obstacles)){best=point;score=distance;}
        }
        return best;
    }
    private static boolean free(Point point,int width,int height,int sizeX,int sizeY,List<Rect> obstacles){
        if(point.x<GAP||point.y<GAP||point.x+sizeX>width-GAP||point.y+sizeY>height-GAP)return false;
        for(var rect:obstacles)if(point.x<rect.x+rect.width+GAP&&point.x+sizeX+GAP>rect.x&&point.y<rect.y+rect.height+GAP&&point.y+sizeY+GAP>rect.y)return false;
        return true;
    }
}
