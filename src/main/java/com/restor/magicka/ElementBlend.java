package com.restor.magicka;

public record ElementBlend(int steam,int ice,int shield,int magma,int fire,int water,int cold,int lightning,int earth,int life,int arcane,int death) {
    public static ElementBlend of(SpellFormula formula) {
        int steam=Math.min(formula.fire(),formula.water()),fire=formula.fire()-steam,water=formula.water()-steam;
        int ice=Math.min(water,formula.cold()),cold=formula.cold()-ice;water-=ice;
        int shield=Math.min(formula.earth(),formula.arcane()),earth=formula.earth()-shield,arcane=formula.arcane()-shield;
        int magma=Math.min(fire,earth);fire-=magma;earth-=magma;magma+=formula.magma();
        return new ElementBlend(steam,ice,shield,magma,fire,water,cold,formula.lightning(),earth,formula.life(),arcane,formula.death());
    }
    public static int composite(Element first,Element second) {
        if(first==Element.FIRE && second==Element.WATER || first==Element.WATER && second==Element.FIRE)return 0;
        if(first==Element.WATER && second==Element.COLD || first==Element.COLD && second==Element.WATER)return 1;
        if(first==Element.EARTH && second==Element.ARCANE || first==Element.ARCANE && second==Element.EARTH)return 2;
        if(first==Element.FIRE && second==Element.EARTH || first==Element.EARTH && second==Element.FIRE)return 3;
        return -1;
    }
    public int count(Element element) {
        return switch(element){case FIRE->fire;case WATER->water;case COLD->cold;case LIGHTNING->lightning;case EARTH->earth;case LIFE->life;case ARCANE->arcane;case DEATH->death;case STEAM->steam;case ICE->ice;case SHIELD->shield;case MAGMA->magma;};
    }
    public int countBase(Element element) {
        return switch(element){case FIRE->fire;case WATER->water;case COLD->cold;case LIGHTNING->lightning;case EARTH->earth;case LIFE->life;case ARCANE->arcane;case DEATH->death;default->0;};
    }
    public int size(){return steam+ice+shield+magma+fire+water+cold+lightning+earth+life+arcane+death;}
    public int color(){return steam>0?0xFFD8D4DC:ice>0?0xFF72E4F2:shield>0?0xFFFFD95A:magma>0?Element.MAGMA.color:death>0?Element.DEATH.color:lightning>0?Element.LIGHTNING.color:fire>0?Element.FIRE.color:cold>0?Element.COLD.color:life>0?Element.LIFE.color:arcane>0?Element.ARCANE.color:water>0?Element.WATER.color:Element.EARTH.color;}
}
