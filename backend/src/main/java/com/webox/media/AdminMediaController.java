package com.webox.media;
import com.webox.auth.UserIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {
    private final MediaService media;
    public AdminMediaController(MediaService media){this.media=media;}
    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public MediaService.Media upload(@AuthenticationPrincipal UserIdentity user,@RequestPart("file") MultipartFile file) {return media.upload(user,file);}
}
