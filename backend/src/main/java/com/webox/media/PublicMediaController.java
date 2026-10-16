package com.webox.media;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import com.webox.shared.ApiException;
import com.webox.shared.ValueRules;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class PublicMediaController {
    private final MediaRepository media;private final Path root;
    public PublicMediaController(MediaRepository media,Path mediaRoot) { this.media=media;this.root=mediaRoot; }
    @GetMapping("/media/{mediaId}") public ResponseEntity<Resource> image(@PathVariable String mediaId) {
        var asset=media.find(ValueRules.uuid(mediaId)).orElseThrow(PublicMediaController::missing);
        try {
            Path path=root.resolve(asset.storageKey()).normalize();
            if(!path.startsWith(root) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw missing();
            Path real=path.toRealPath();if(!real.startsWith(root.toRealPath()) || Files.size(real)!=asset.bytes()) throw missing();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mime())).contentLength(asset.bytes())
                .cacheControl(CacheControl.maxAge(365,TimeUnit.DAYS).cachePublic().immutable()).header("X-Content-Type-Options","nosniff")
                .eTag("\""+asset.sha256()+"\"").body(new FileSystemResource(real));
        } catch(java.io.IOException e) { throw missing(); }
    }
    private static ApiException missing() { return new ApiException(404,"NOT_FOUND","This image is not available."); }
}
