package com.webox.support;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import com.webox.auth.AuthService;
import com.webox.media.MediaRepository;
import com.webox.shared.DomainTypes.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verified images and relational fixtures in the disposable test database only. */
public final class CatalogFixtures {
    private static final AtomicInteger DATES = new AtomicInteger();
    private final JdbcTemplate jdbc;
    private final MediaRepository media;
    private final Path root;
    private final FixtureFactory users;
    private final TransactionTemplate transaction;
    private static final LocalDateTime NOW = LocalDateTime.of(2026,10,16,1,0);
    public CatalogFixtures(JdbcTemplate jdbc, MediaRepository media, Path root, JdbcIndexedSessionRepository sessions, AuthService auth, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.media=media;this.root=root;this.users=new FixtureFactory(sessions,auth);this.transaction=new TransactionTemplate(manager);
    }
    public record Menu(LocalDate date,String adminId,String burger,String pasta,String salad,String chinese,String japanese,String hidden,String unscheduled,String mediaId,Map<String,String> options) {}
    public Menu create() {
        var admin=users.identity(Role.Admin).user().userId();
        String burgerImage=image(admin,"classic-beef-burger.png"),pastaImage=image(admin,"tomato-pasta.png"),saladImage=image(admin,"garden-salad.jpg");
        return transaction.execute(status->{
            LocalDate date=LocalDate.of(2032,1,1).plusDays(DATES.getAndIncrement());
            jdbc.update("INSERT INTO daily_menus VALUES (?,?,?,?)",date,1,admin,NOW);
            var options=new LinkedHashMap<String,String>();
            String burger=dish(date,"Classic Beef Burger","Grilled beef, lettuce and tomato. Customize your bun and sauce.",3200,"Western","Beef",burgerImage,true,true,20);
            group(burger,"Bun",true,"Single",0,options,new Choice("Plain bun",0,"Gluten"),new Choice("Brioche bun",200,"Gluten","Egg","Dairy"));
            group(burger,"Sauce",true,"Single",1,options,new Choice("Ketchup",0),new Choice("Mayonnaise",0,"Egg"));
            group(burger,"Burger extras",false,"Multiple",2,options,new Choice("Extra cheese",300,"Dairy"),new Choice("Extra tomato",100));
            jdbc.update("INSERT INTO dish_allergens VALUES (?,?)",burger,"Gluten");
            String pasta=dish(date,"Tomato Pasta","Pasta with tomato sauce and fresh basil.",2650,"Western","Wheat",pastaImage,true,true,20);
            group(pasta,"Pasta type",true,"Single",0,options,new Choice("Spaghetti",0,"Gluten"),new Choice("Fusilli",0,"Gluten"));
            group(pasta,"Add-ons",false,"Multiple",1,options,new Choice("Cheese",300,"Dairy"),new Choice("Extra basil",100));
            jdbc.update("INSERT INTO dish_allergens VALUES (?,?)",pasta,"Gluten");
            String salad=dish(date,"Garden Salad","Fresh greens, chickpeas and a bright dressing.",1800,"Light Meal","Chickpeas",saladImage,true,true,0);
            String chinese=dish(date,"Chinese Garden Salad","Fresh greens with a 100% plant-based dressing_!.",1800,"Chinese","Chickpeas",saladImage,true,true,20);
            String japanese=dish(date,"Japanese Garden Salad","Fresh greens with soy dressing.",1800,"Japanese","Chickpeas",saladImage,true,true,20);
            String hidden=dish(date,"Hidden Salad","An unpublished dish.",1800,"Light Meal","Chickpeas",saladImage,false,true,20);
            String unscheduled=dish(date,"Unscheduled Salad","A dish not on this date.",1800,"Light Meal","Chickpeas",saladImage,true,false,20);
            return new Menu(date,admin,burger,pasta,salad,chinese,japanese,hidden,unscheduled,saladImage,Map.copyOf(options));
        });
    }
    public String dish(LocalDate date,String name,String description,long price,String category,String protein,String image,boolean listed,boolean scheduled,int total) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO dishes VALUES (?,?,?,?,?,?,?, ?,?,?,?,?)",id,name,description,price,category,protein,"None",image,listed,1,NOW,NOW);
        jdbc.update("INSERT INTO daily_supply VALUES (?,?,?,?,?,?,?)",date,id,scheduled,total,0,0,NOW);return id;
    }
    private record Choice(String name,long extra,String... allergens) {}
    private void group(String dish,String name,boolean required,String mode,int order,Map<String,String> options,Choice... choices) {
        String group=UUID.randomUUID().toString();jdbc.update("INSERT INTO customization_groups VALUES (?,?,?,?,?,?)",group,dish,name,mode,required,order);
        for(int i=0;i<choices.length;i++) {
            var choice=choices[i];String id=UUID.randomUUID().toString();options.put(choice.name(),id);
            jdbc.update("INSERT INTO customization_options VALUES (?,?,?,?,?)",id,group,choice.name(),choice.extra(),i);
            for(String allergen:choice.allergens()) jdbc.update("INSERT INTO option_allergens VALUES (?,?)",id,allergen);
        }
    }
    private String image(String admin,String filename) {
        try(var stream=CatalogFixtures.class.getResourceAsStream("/seed/images/"+filename)) {
            if(stream==null) throw new IllegalStateException("Missing image fixture");
            byte[] bytes=stream.readAllBytes();var decoded=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if(decoded==null || decoded.getWidth()>8192 || decoded.getHeight()>8192 || (long)decoded.getWidth()*decoded.getHeight()>16777216 || bytes.length>5242880) throw new IllegalStateException("Invalid image fixture");
            String id=UUID.randomUUID().toString(),format=filename.endsWith(".png")?"png":"jpeg",key=id+"."+format;
            var output=new java.io.ByteArrayOutputStream();if(!ImageIO.write(decoded,format,output)) throw new IllegalStateException("Image encoder missing");
            byte[] served=output.toByteArray();Files.write(root.resolve(key),served,StandardOpenOption.CREATE_NEW);
            String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(served));
            media.insert(new MediaRepository.Asset(id,key,"image/"+format,served.length,sha,decoded.getWidth(),decoded.getHeight()),admin,filename,"image/"+format,bytes.length,"Test fixture; see seed/ATTRIBUTION.md",NOW.toInstant(ZoneOffset.UTC));return id;
        } catch(java.io.IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("Cannot prepare verified test image",e); }
    }
}
