import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CompositeIcons {
    static Graphics2D g;
    static void polygon(int color,int... points) {
        int n=points.length/2;int[] x=new int[n],y=new int[n];
        for(int i=0;i<n;i++){x[i]=points[i*2];y[i]=points[i*2+1];}
        g.setColor(new Color(color));g.fillPolygon(x,y,n);
    }
    static void rect(int color,int x,int y,int w,int h){g.setColor(new Color(color));g.fillRect(x,y,w,h);}
    public static void main(String[] args) throws Exception {
        String root="src/main/resources/assets/magicka/textures/gui/";
        BufferedImage base=ImageIO.read(new File(root+"elements.png"));
        BufferedImage out=new BufferedImage(96,32,BufferedImage.TYPE_INT_ARGB);
        int[] backgrounds={0x555361,0x365967,0x796335};
        for(int c=0;c<3;c++){
            for(int y=0;y<32;y++)for(int x=0;x<32;x++)out.setRGB(c*32+x,y,(base.getRGB(32+x,y)&0xff000000)|backgrounds[c]);
            g=out.createGraphics();g.translate(c*32,0);
            if(c==0){
                polygon(0x32313E,3,15,7,15,7,10,12,10,12,6,20,6,20,10,25,10,25,14,29,14,29,23,25,23,25,26,7,26,7,23,3,23);
                polygon(0xC3C6CC,6,17,10,17,10,12,15,12,15,9,18,9,18,12,23,12,23,17,26,17,26,21,23,21,23,23,9,23,9,21,6,21);
                rect(0xECEEEF,15,9,3,8);rect(0xECEEEF,10,13,5,5);rect(0xECEEEF,7,18,5,3);
                rect(0x999EAC,12,21,11,2);
            }else if(c==1){
                polygon(0x1D3A46,17,2,23,2,23,7,27,7,27,17,22,17,22,23,17,23,17,29,9,29,9,25,5,25,5,15,9,15,9,10,13,10,13,6,17,6);
                polygon(0x72CEDD,18,6,20,6,20,10,23,10,23,15,19,15,19,21,14,21,14,25,10,25,9,22,9,17,12,17,12,13,16,13,16,9,18,9);
                polygon(0xC5F3F5,18,6,20,6,20,10,17,10,17,14,14,14,14,19,10,23,10,17,12,17,12,13,16,13,16,9,18,9);
                rect(0x4397B0,14,21,3,4);
            }else{
                polygon(0x4B3619,5,5,27,5,27,17,24,17,24,23,20,23,20,27,16,30,12,27,12,24,8,24,8,18,5,18);
                polygon(0xF1CC42,8,8,24,8,24,16,21,16,21,21,18,21,18,25,16,27,14,25,14,22,11,22,11,17,8,17);
                polygon(0xFFF294,8,8,14,8,14,20,11,20,11,17,8,17);
                polygon(0xC68F28,20,8,24,8,24,16,21,16,21,21,18,21,18,25,16,27,16,23,18,20,18,15,20,15);
            }
            g.dispose();
        }
        ImageIO.write(out,"png",new File(root+"composites.png"));
        BufferedImage preview=new BufferedImage(352*4,32*4,BufferedImage.TYPE_INT_ARGB);
        g=preview.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(base,0,0,1024,128,null);g.drawImage(out,1024,0,384,128,null);g.dispose();
        ImageIO.write(preview,"png",new File("build/composite-icons-preview.png"));
    }
}
