package com.webox.media;
import com.webox.support.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class MediaUploadIT extends AdminTestSupport {
    @org.springframework.beans.factory.annotation.Autowired MediaService mediaService;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ImageCodec codec;
    private MockHttpServletResponse upload(Browser user,byte[] bytes,String mime,String name) throws Exception {
        return mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file",name,mime,bytes)).cookie(user.identity().cookie()).header("X-XSRF-TOKEN",user.csrf())).andReturn().getResponse();
    }
    private byte[] image(String format,int width,int height) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setColor(java.awt.Color.ORANGE);graphics.fillRect(0,0,width,height);graphics.dispose();var bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,format,bytes));return bytes.toByteArray();
    }
    @Test void genuineJpegPngAndWebpAreDecodedAndConvertedToImmutableDisplayMedia() throws Exception {
        var admin=admin();byte[] webp;try(var input=getClass().getResourceAsStream("/media/blue-tile.webp")){assertNotNull(input);webp=input.readAllBytes();}
        var sources=List.of(image("jpeg",8,8),image("png",8,8),webp);var types=List.of("image/jpeg","image/png","image/webp");
        for(int i=0;i<sources.size();i++) {
            var response=upload(admin,sources.get(i),types.get(i),"../../display-image");assertEquals(201,response.getStatus());ContractAssertions.matches("Media",responseBody(response));var tree=json.readTree(responseBody(response));String id=field(response,"mediaId");var asset=media.find(id).orElseThrow();assertEquals(tree.path("byteLength").asLong(),Files.size(MEDIA_ROOT.resolve(asset.storageKey())));assertFalse(asset.storageKey().contains(".."));assertTrue(Set.of("image/jpeg","image/png").contains(asset.mime()));
            var served=mvc.perform(get(field(response,"url"))).andReturn().getResponse();assertEquals(200,served.getStatus());assertNotNull(ImageIO.read(new ByteArrayInputStream(served.getContentAsByteArray())));assertEquals(asset.bytes(),served.getContentAsByteArray().length);assertEquals("nosniff",served.getHeader("X-Content-Type-Options"));assertEquals(types.get(i),jdbc.queryForObject("SELECT source_mime FROM media_assets WHERE id=?",String.class,id));
        }
    }
    @Test void exactlyFiveMiBInputIsAllowedAndOneExtraByteOrInvalidImageIsRejected() throws Exception {
        var admin=admin();byte[] valid=image("png",16,16);var exact=Arrays.copyOf(valid,5_242_880);var accepted=upload(admin,exact,"image/png","boundary.png");assertEquals(201,accepted.getStatus());ContractAssertions.matches("Media",responseBody(accepted));assertEquals(5_242_880,jdbc.queryForObject("SELECT source_bytes FROM media_assets WHERE id=?",Integer.class,field(accepted,"mediaId")));
        error(upload(admin,Arrays.copyOf(valid,5_242_881),"image/png","large.png"),413,"IMAGE_TOO_LARGE");
        error(upload(admin,"<svg><script>alert(1)</script></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8),"image/png","fake.png"),415,"IMAGE_TYPE_UNSUPPORTED");
        error(upload(admin,valid,"image/jpeg","wrong.jpg"),415,"IMAGE_TYPE_UNSUPPORTED");error(upload(admin,Arrays.copyOf(valid,valid.length/2),"image/png","truncated.png"),422,"IMAGE_INVALID");error(upload(admin,new byte[0],"image/png","empty.png"),422,"IMAGE_INVALID");
    }
    @Test void pixelLimitsAreAppliedBeforeFullDecodeAndFailedReplacementKeepsTheOldReference() throws Exception {
        var admin=admin();var fixture=menu();String before=jdbc.queryForObject("SELECT media_id FROM dishes WHERE id=?",String.class,fixture.pasta());
        error(upload(admin,image("png",8193,1),"image/png","wide.png"),422,"IMAGE_INVALID");error(upload(admin,image("png",4097,4096),"image/png","pixels.png"),422,"IMAGE_INVALID");
        assertEquals(before,jdbc.queryForObject("SELECT media_id FROM dishes WHERE id=?",String.class,fixture.pasta()));assertEquals(200,mvc.perform(get("/media/"+before)).andReturn().getResponse().getStatus());
        try(var paths=Files.list(MEDIA_ROOT)) {assertTrue(paths.noneMatch(path->path.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void roleAndCsrfApplyBeforeAnyMediaIsStored() throws Exception {
        var admin=admin();var employee=browser();byte[] bytes=image("png",8,8);assertEquals(201,upload(admin,bytes,"image/png","valid.png").getStatus());long before=jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class);
        error(upload(employee,bytes,"image/png","staff.png"),403,"FORBIDDEN");assertEquals(403,mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file","no-csrf.png","image/png",bytes)).cookie(admin.identity().cookie())).andReturn().getResponse().getStatus());assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class));
    }
    @Test void realSmallWebpThatExpandsPastTheOutputCapIsRejectedWithoutStoringAnything() throws Exception {
        byte[] source;try(var input=getClass().getResourceAsStream("/media/expanding-noise.webp")){assertNotNull(input);source=input.readAllBytes();}
        assertTrue(source.length<ImageCodec.MAX_BYTES);var admin=admin();long before=jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class);
        error(upload(admin,source,"image/webp","expanding.webp"),413,"IMAGE_TOO_LARGE");assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class));
        try(var files=Files.list(MEDIA_ROOT)){assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void derivativeBoundaryAndConversionFailureKeepTheExistingDishImage() throws Exception {
        var admin=admin();var fixture=menu();String old=jdbc.queryForObject("SELECT media_id FROM dishes WHERE id=?",String.class,fixture.pasta());byte[] png=image("png",8,8);
        // The real decoder still validates the input. The codec seam gives exact byte boundaries.
        org.mockito.Mockito.doReturn(Arrays.copyOf(png,ImageCodec.MAX_BYTES)).when(codec).encode(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("png"));
        var exact=upload(admin,png,"image/png","exact.png");assertEquals(201,exact.getStatus());assertEquals(ImageCodec.MAX_BYTES,json.readTree(responseBody(exact)).path("byteLength").asInt());assertNotNull(ImageIO.read(MEDIA_ROOT.resolve(media.find(field(exact,"mediaId")).orElseThrow().storageKey()).toFile()));
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class);
        org.mockito.Mockito.doReturn(Arrays.copyOf(png,ImageCodec.MAX_BYTES+1)).when(codec).encode(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("png"));error(upload(admin,png,"image/png","oversized.png"),413,"IMAGE_TOO_LARGE");
        org.mockito.Mockito.doThrow(ImageCodec.invalid()).when(codec).encode(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("png"));error(upload(admin,png,"image/png","failed.png"),422,"IMAGE_INVALID");assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class));assertEquals(old,jdbc.queryForObject("SELECT media_id FROM dishes WHERE id=?",String.class,fixture.pasta()));assertEquals(200,mvc.perform(get("/media/"+old)).andReturn().getResponse().getStatus());
    }
    @Test void rollbackAfterFileAndDatabaseInsertRemovesTheNewFileAndRecord() throws Exception {
        var admin=admin();byte[] png=image("png",8,8);var id=new java.util.concurrent.atomic.AtomicReference<String>();var path=new java.util.concurrent.atomic.AtomicReference<Path>();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
            var result=mediaService.store(admin.identity().user(),png,"image/png","rollback.png",null);id.set(result.mediaId());path.set(MEDIA_ROOT.resolve(media.find(result.mediaId()).orElseThrow().storageKey()));assertTrue(Files.exists(path.get()));status.setRollbackOnly();
        });assertTrue(media.find(id.get()).isEmpty());assertFalse(Files.exists(path.get()));
    }
}
