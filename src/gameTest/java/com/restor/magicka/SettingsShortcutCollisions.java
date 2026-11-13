package com.restor.magicka;

import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid=Magicka.ID,value=Dist.CLIENT)
public final class SettingsShortcutCollisions {
    private static Blocker blocker;
    private static final class Blocker extends Button {
        Blocker(int x,int y){super(x,y,20,20,Component.literal("X"),b->{throw new IllegalStateException("Another mod received the shortcut click");},DEFAULT_NARRATION);}
        void cover(int width,int height){setPosition(0,0);setWidth(width);this.height=height;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void lateButtons(ScreenEvent.Init.Post event){
        if(!Boolean.getBoolean("magicka.settingsApplicationTest"))return;
        String key=event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen?"menu.options":event.getScreen() instanceof net.minecraft.client.gui.screens.options.OptionsScreen?"gui.done":null;
        if(key==null)return;
        var anchor=event.getListenersList().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Component.translatable(key).getString())).map(c->(Button)c).findFirst().orElseThrow();
        blocker=new Blocker(anchor.getX()-24,anchor.getY()+(anchor.getHeight()-20)/2);event.addListener(blocker);
        event.addListener(Button.builder(Component.literal("X"),b->{throw new IllegalStateException("Another mod received the shortcut click");}).bounds(anchor.getX()+anchor.getWidth()+4,blocker.getY(),20,20).build());
    }
    static Button check(Screen screen){
        var shortcut=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Component.translatable("magicka.configuration.title").getString())).map(c->(Button)c).findFirst().orElseThrow();
        if(!shortcut.visible)throw new IllegalStateException("Shortcut should find a free position");
        if(shortcut.getX()<4||shortcut.getY()<4||shortcut.getX()+shortcut.getWidth()>screen.width-4||shortcut.getY()+shortcut.getHeight()>screen.height-4)throw new IllegalStateException("Shortcut clipped by viewport");
        for(var child:screen.children())if(child!=shortcut&&child instanceof AbstractWidget other&&other.visible){
            int dx=Math.max(other.getX()-shortcut.getX()-shortcut.getWidth(),shortcut.getX()-other.getX()-other.getWidth()),dy=Math.max(other.getY()-shortcut.getY()-shortcut.getHeight(),shortcut.getY()-other.getY()-other.getHeight());
            if(dx<4&&dy<4)throw new IllegalStateException("Shortcut intersects "+other.getMessage().getString());
        }
        return shortcut;
    }
    static void cover(Screen screen){blocker.cover(screen.width,screen.height);}
    static void uncover(){blocker.visible=false;}
    private SettingsShortcutCollisions(){}
}
