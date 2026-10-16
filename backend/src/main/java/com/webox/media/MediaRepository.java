package com.webox.media;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class MediaRepository {
    private final JdbcTemplate jdbc;
    public MediaRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Asset> find(String id) {
        return jdbc.query("SELECT id,storage_key,served_mime,served_bytes,content_sha256,width,height FROM media_assets WHERE id=?",
            (rs,n)->new Asset(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getInt(6),rs.getInt(7)),id).stream().findFirst();
    }
    public void insert(Asset asset,String uploader,String originalName,String sourceMime,long sourceBytes,String attribution,Instant now) {
        if(!"Admin".equals(jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,uploader))) throw new IllegalArgumentException("Only administrators can create media.");
        jdbc.update("INSERT INTO media_assets(id,uploaded_by,original_filename,source_mime,source_bytes,content_sha256,width,height,storage_key,served_mime,served_bytes,source_attribution,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            asset.id(),uploader,originalName,sourceMime,sourceBytes,asset.sha256(),asset.width(),asset.height(),asset.storageKey(),asset.mime(),asset.bytes(),attribution,LocalDateTime.ofInstant(now,ZoneOffset.UTC));
    }
    public record Asset(String id,String storageKey,String mime,long bytes,String sha256,int width,int height) {}
}
