package com.webox.media;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import com.webox.shared.ApiException;
import org.springframework.stereotype.Component;
@Component
public class ImageCodec {
    public static final int MAX_BYTES=5_242_880,MAX_SIDE=8192;
    public static final long MAX_PIXELS=16_777_216;
    public record Decoded(BufferedImage image,String sourceMime) {}
    public Decoded decode(byte[] bytes,String claimedMime) {
        if(bytes.length==0) throw invalid();if(bytes.length>MAX_BYTES) throw tooLarge();
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext()) throw unsupported();var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(format.equals("jpg"))format="jpeg";
                if(!Set.of("jpeg","png","webp").contains(format))throw unsupported();String mime="image/"+format;
                if(claimedMime==null || !mime.equals(claimedMime.split(";",2)[0].strip().toLowerCase(Locale.ROOT)))throw unsupported();
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>MAX_SIDE || height>MAX_SIDE || (long)width*height>MAX_PIXELS)throw invalid();
                var warning=new java.util.concurrent.atomic.AtomicBoolean();reader.addIIOReadWarningListener((source,message)->warning.set(true));var decoded=reader.read(0);
                if(decoded==null || warning.get() || decoded.getWidth()!=width || decoded.getHeight()!=height)throw invalid();
                return new Decoded(decoded,mime);
            } finally {reader.dispose();}
        } catch(ApiException e) {throw e;}catch(IOException | RuntimeException e) {throw invalid();}
    }
    public byte[] encode(BufferedImage image,String format) {
        // The cap applies while writing and again at the service boundary.
        var exceeded=new java.util.concurrent.atomic.AtomicBoolean();
        var output=new ByteArrayOutputStream() {
            private void check(int count) {if((long)size()+count>MAX_BYTES){exceeded.set(true);throw tooLarge();}}
            @Override public synchronized void write(int value) {check(1);super.write(value);}
            @Override public synchronized void write(byte[] bytes,int offset,int length) {check(length);super.write(bytes,offset,length);}
        };
        try {if(!ImageIO.write(image,format,output))throw invalid();return output.toByteArray();}
        catch(ApiException e) {throw e;}catch(IOException | RuntimeException e) {if(exceeded.get())throw tooLarge();throw invalid();}
    }
    public static ApiException invalid() {return new ApiException(422,"IMAGE_INVALID","Use a complete image no larger than 8192 pixels per side and 16,777,216 pixels in total.");}
    public static ApiException unsupported() {return new ApiException(415,"IMAGE_TYPE_UNSUPPORTED","Upload a JPEG, PNG or WebP image with its matching content type.");}
    public static ApiException tooLarge() {return new ApiException(413,"IMAGE_TOO_LARGE","The uploaded image and its display copy must each be at most 5 MiB.");}
}
