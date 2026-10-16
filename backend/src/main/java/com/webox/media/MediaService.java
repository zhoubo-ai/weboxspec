package com.webox.media;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.webox.auth.UserIdentity;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
@Service
public class MediaService {
    private final MediaRepository media;private final ImageCodec codec;private final Path root;private final BusinessClock clock;
    public MediaService(MediaRepository media,ImageCodec codec,Path mediaRoot,BusinessClock clock) throws IOException {this.media=media;this.codec=codec;this.root=mediaRoot.toRealPath();this.clock=clock;}
    public record Media(String mediaId,String url,String contentType,long byteLength) {}
    @Transactional(isolation=Isolation.READ_COMMITTED) public Media upload(UserIdentity actor,MultipartFile file) {
        if(actor.role()!=Role.Admin)throw new ApiException(403,"FORBIDDEN","Administrator access is required.");
        if(file==null || file.isEmpty())throw ImageCodec.invalid();if(file.getSize()>ImageCodec.MAX_BYTES)throw ImageCodec.tooLarge();
        try(var input=file.getInputStream()) {return store(actor,input.readNBytes(ImageCodec.MAX_BYTES+1),file.getContentType(),file.getOriginalFilename(),null);}
        catch(IOException e) {throw ImageCodec.invalid();}
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Media store(UserIdentity actor,byte[] source,String mime,String original,String attribution) {
        if(actor.role()!=Role.Admin)throw new ApiException(403,"FORBIDDEN","Administrator access is required.");
        var decoded=codec.decode(source,mime);String format=decoded.sourceMime().equals("image/jpeg")?"jpeg":"png";byte[] encoded;
        try {encoded=codec.encode(decoded.image(),format);}finally{decoded.image().flush();}
        if(encoded.length==0)throw ImageCodec.invalid();if(encoded.length>ImageCodec.MAX_BYTES)throw ImageCodec.tooLarge();
        String name=original==null || original.isBlank()?"upload":original;if(ValueRules.codePoints(name)>255)name=name.substring(0,name.offsetByCodePoints(0,255));
        String id=UUID.randomUUID().toString(),key=id+"."+format;Path destination=root.resolve(key);Path temporary=null;
        try {
            temporary=Files.createTempFile(root,"upload-",".tmp");Files.write(temporary,encoded);Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED)delete(destination);} });
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
            media.insert(new MediaRepository.Asset(id,key,"image/"+format,encoded.length,sha,decoded.image().getWidth(),decoded.image().getHeight()),actor.userId(),name,decoded.sourceMime(),source.length,attribution,clock.now());
            return new Media(id,"/media/"+id,"image/"+format,encoded.length);
        } catch(IOException | NoSuchAlgorithmException e) {delete(destination);throw new ApiException(503,"TEMPORARY_UNAVAILABLE","The image could not be stored. Please try again.",true,Map.of());}
        catch(RuntimeException e) {delete(destination);throw e;}
        finally {if(temporary!=null)delete(temporary);}
    }
    private static void delete(Path path) {try{Files.deleteIfExists(path);}catch(IOException ignored){/* Unreferenced files are handled by media maintenance. */}}
}
