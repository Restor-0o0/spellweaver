package com.restor.magicka;

import com.restor.magicka.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid=Magicka.ID,value=Dist.CLIENT)
public final class BookCoverClientTest {
    private static int stage,ticks,watchdog,scale;
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static Object field(String name){try{var field=GrimoireScreen.class.getDeclaredField(name);field.setAccessible(true);return field.get(Minecraft.getInstance().screen);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static Button arrow(String glyph){var mc=Minecraft.getInstance();return mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(glyph)).map(c->(Button)c).findFirst().orElseThrow();}
    private static void next(int value){stage=value;ticks=0;watchdog=0;}
    private static void screenshot(String name){var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("magicka.bookCoverTest"))return;var mc=Minecraft.getInstance();if(++watchdog>2400)throw new IllegalStateException("Book cover test stuck at "+stage);

        if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&++ticks==20){
            net.minecraftforge.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();scale=mc.options.guiScale().get();mc.options.guiScale().set(1);mc.resizeDisplay();
            var prefs=BookPreferences.get();prefs.query="";prefs.tag="all";prefs.elements=0;prefs.selectedId="";mc.setScreen(new GrimoireScreen());next(1);
        }else if(stage==1&&++ticks==30){
            check(!(Boolean)field("single"),"Wide test must show a spread");check(arrow("◀").active,"Back arrow should close the first spread");arrow("◀").onPress();next(2);
        }else if(stage==2&&++ticks==20){
            check((Boolean)field("coverClosed"),"First back did not close the cover");check(mc.screen.children().size()==2,"Closed cover should hide spell controls");screenshot("grimoire-closed-wide.png");mc.screen.mouseScrolled(0,0,1);check((Boolean)field("coverClosed"),"Scrolling further back must keep the cover visible");arrow("▶").onPress();next(3);
        }else if(stage==3&&++ticks==20){
            check(!(Boolean)field("coverClosed")&&(Integer)field("page")==0,"Forward must reopen at the first page");arrow("▶").onPress();next(4);
        }else if(stage==4&&++ticks==20){
            check((Integer)field("page")>=2,"Forward must still turn pages normally");arrow("◀").onPress();next(5);
        }else if(stage==5&&++ticks==20){
            check(!(Boolean)field("coverClosed")&&(Integer)field("page")==0,"Back from the second spread must return to the first");arrow("◀").onPress();next(6);
        }else if(stage==6&&++ticks==20){
            int center=(Integer)field("spine")+(Integer)field("half")/2;check(Math.abs(center-mc.screen.width/2)<=1,"Closed cover should be centered");check(mc.screen.mouseClicked(center,(Integer)field("y")+30,0),"Cover click was not consumed");next(7);
        }else if(stage==7&&++ticks==20){
            check(!(Boolean)field("coverClosed"),"Clicking the cover must reopen it");mc.options.guiScale().set(2);mc.resizeDisplay();next(8);
        }else if(stage==8&&++ticks==20){
            check((Boolean)field("single"),"Narrow test must use one page");mc.screen.mouseScrolled(0,0,1);next(9);
        }else if(stage==9&&++ticks==20){
            check((Boolean)field("coverClosed"),"Narrow cover did not close");screenshot("grimoire-closed-narrow.png");mc.screen.mouseScrolled(0,0,-1);next(10);
        }else if(stage==10&&++ticks==20){
            check(!(Boolean)field("coverClosed"),"Scrolling forward must reopen the cover");mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected(mc.getLanguageManager().getLanguage("ru_ru"));mc.reloadResourcePacks();next(11);
        }else if(stage==11&&mc.getOverlay()==null&&++ticks==20){
            arrow("◀").onPress();next(12);
        }else if(stage==12&&++ticks==20){
            screenshot("grimoire-closed-ru.png");org.slf4j.LoggerFactory.getLogger("BookCoverTest").info("BOOK_COVER_OK: first-spread closing, stable cover, forward/click reopening, ordinary navigation, wide/narrow and Russian/English verified");mc.options.guiScale().set(scale);mc.resizeDisplay();mc.stop();
        }
    }
    private BookCoverClientTest(){}
}
