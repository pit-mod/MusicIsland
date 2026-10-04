package io.github.pitmod.musicisland.music;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Dominant saturated cover colour, quantized so artwork text/background noise does not win. */
public final class ArtworkAccent {
    private ArtworkAccent(){}
    public static int from(BufferedImage image){
        if(image==null)return 0xFFAAAAAF;
        return region(image,0,0,image.getWidth(),image.getHeight());
    }
    /** A softly blurred horizontal colour field from the displayed square artwork crop. */
    public static int[] columns(BufferedImage image,int count){
        if(count<=0)return new int[0];
        int[] colors=new int[count];
        if(image==null){java.util.Arrays.fill(colors,0xFFAAAAAF);return colors;}
        int size=Math.min(image.getWidth(),image.getHeight());
        int cropX=(image.getWidth()-size)/2,cropY=(image.getHeight()-size)/2;
        int grid=Math.min(40,size);
        double[][] sums=new double[count][7];
        double coverage=0,visible=0;
        for(int row=0;row<grid;row++)for(int col=0;col<grid;col++){
            double x=(col+.5)/grid,y=(row+.5)/grid;
            int rgb=image.getRGB(cropX+Math.min(size-1,(int)(x*size)),cropY+Math.min(size-1,(int)(y*size)));
            double alpha=(rgb>>>24)/255.;if(alpha<.1)continue;
            double[] lab=toLab(rgb);
            double chroma=Math.hypot(lab[1],lab[2]);
            double colourSupport=Math.max(0,Math.min(1,(chroma-.025)/.06));
            coverage+=alpha*colourSupport;visible+=alpha;
            // White lettering and black borders contribute less than the photograph,
            // but neutral covers still remain neutral instead of inventing an accent.
            double detail=(.30+.70*Math.sin(Math.PI*lab[0]))*(.70+Math.min(1,chroma/.20));
            double vertical=.85+.15*Math.exp(-square((y-.5)/.35)*.5);
            for(int i=0;i<count;i++){
                double center=(i+.5)/count;
                double weight=alpha*detail*vertical*Math.exp(-square((x-center)/.17)*.5);
                sums[i][0]+=weight;for(int channel=0;channel<3;channel++)sums[i][channel+1]+=lab[channel]*weight;
                sums[i][4]+=weight*colourSupport;
                sums[i][5]+=lab[1]*weight*colourSupport;sums[i][6]+=lab[2]*weight*colourSupport;
            }
        }
        for(int i=0;i<count;i++){
            if(sums[i][0]<1e-8){colors[i]=0xFFAAAAAF;continue;}
            double light=sums[i][1]/sums[i][0],a=sums[i][2]/sums[i][0],b=sums[i][3]/sums[i][0];
            if(sums[i][4]>1e-8){
                // Recover colour from light covers without letting a tiny logo recolour
                // an otherwise monochrome sleeve. Gaussian footprints keep this continuous.
                double confidence=Math.max(0,Math.min(1,(coverage/Math.max(1e-8,visible)-.02)/.16))
                        *Math.min(1,sums[i][4]/sums[i][0]/.18);
                a+=(sums[i][5]/sums[i][4]-a)*confidence;
                b+=(sums[i][6]/sums[i][4]-b)*confidence;
            }
            double chroma=Math.hypot(a,b),gain=chroma<1e-8?0:Math.min(1.25,.16/chroma);
            // Consistent perceived brightness on black, with hue-preserving gamut mapping.
            colors[i]=fromLab(.78+.06*(light-.55),a*gain,b*gain);
        }
        return colors;
    }
    /** Perceptual interpolation also keeps cover-to-cover transitions free of muddy midtones. */
    public static int blend(int first,int second,double amount){
        if(amount<=0)return first;if(amount>=1)return second;
        double[] a=toLab(first),b=toLab(second);
        return fromLab(a[0]+(b[0]-a[0])*amount,a[1]+(b[1]-a[1])*amount,a[2]+(b[2]-a[2])*amount);
    }
    private static double square(double value){return value*value;}
    private static double linear(int channel){double v=channel/255.;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double[] toLab(int rgb){
        double r=linear((rgb>>16)&255),g=linear((rgb>>8)&255),b=linear(rgb&255);
        double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
        double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
        double s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
        return new double[]{.2104542553*l+.7936177850*m-.0040720468*s,
                1.9779984951*l-2.4285922050*m+.4505937099*s,
                .0259040371*l+.7827717662*m-.8086757660*s};
    }
    private static double[] rgb(double light,double a,double b){
        double l=light+.3963377774*a+.2158037573*b,m=light-.1055613458*a-.0638541728*b,s=light-.0894841775*a-1.2914855480*b;
        l=l*l*l;m=m*m*m;s=s*s*s;
        return new double[]{4.0767416621*l-3.3077115913*m+.2309699292*s,
                -1.2684380046*l+2.6097574011*m-.3413193965*s,
                -.0041960863*l-.7034186147*m+1.7076147010*s};
    }
    private static boolean inGamut(double[] rgb){for(double c:rgb)if(c<0||c>1)return false;return true;}
    private static int fromLab(double light,double a,double b){
        double[] channels=rgb(light,a,b);
        if(!inGamut(channels)){
            double low=0,high=1;
            for(int i=0;i<10;i++){double mid=(low+high)*.5;if(inGamut(rgb(light,a*mid,b*mid)))low=mid;else high=mid;}
            channels=rgb(light,a*low,b*low);
        }
        return 0xFF000000|(encoded(channels[0])<<16)|(encoded(channels[1])<<8)|encoded(channels[2]);
    }
    private static int encoded(double linear){double v=Math.max(0,Math.min(1,linear));return (int)Math.round(255*(v<=.0031308?12.92*v:1.055*Math.pow(v,1/2.4)-.055));}
    private static int region(BufferedImage image,int left,int top,int right,int bottom){
        double[] weights=new double[24],red=new double[24],green=new double[24],blue=new double[24];
        for(int y=top;y<bottom;y+=Math.max(1,(bottom-top)/24))for(int x=left;x<right;x+=Math.max(1,(right-left)/12)){
            int c=image.getRGB(x,y),r=(c>>16)&255,g=(c>>8)&255,b=c&255;if((c>>>24)<128)continue;
            float[] h=Color.RGBtoHSB(r,g,b,null);if(h[2]<.13f||h[1]<.12f)continue;
            int bin=Math.min(23,(int)(h[0]*24));double weight=h[1]*h[1]*Math.sqrt(h[2]);weights[bin]+=weight;red[bin]+=r*weight;green[bin]+=g*weight;blue[bin]+=b*weight;
        }
        int winner=0;for(int i=1;i<24;i++)if(weights[i]>weights[winner])winner=i;
        if(weights[winner]==0)return 0xFFAAAAAF;
        float[] h=Color.RGBtoHSB((int)(red[winner]/weights[winner]),(int)(green[winner]/weights[winner]),(int)(blue[winner]/weights[winner]),null);
        return Color.HSBtoRGB(h[0],Math.min(.72f,h[1]),Math.max(.64f,Math.min(.92f,h[2])));
    }
}
