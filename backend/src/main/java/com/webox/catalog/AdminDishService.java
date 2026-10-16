package com.webox.catalog;
import java.sql.Timestamp;
import java.util.*;
import com.webox.auth.UserIdentity;
import com.webox.media.MediaRepository;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.webox.catalog.AdminCatalogModels.*;
@Service
public class AdminDishService {
    private final JdbcTemplate jdbc;private final DishRepository dishes;private final MediaRepository media;private final CatalogChangePublisher changes;private final BusinessClock clock;
    public AdminDishService(JdbcTemplate jdbc,DishRepository dishes,MediaRepository media,CatalogChangePublisher changes,BusinessClock clock){this.jdbc=jdbc;this.dishes=dishes;this.media=media;this.changes=changes;this.clock=clock;}
    private record Root(long version,boolean published,String media) {}
    private Root root(String id,boolean lock) {return jdbc.query("SELECT version,is_listed,media_id FROM dishes WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Root(rs.getLong(1),rs.getBoolean(2),rs.getString(3)),id).stream().findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","This dish was not found."));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdminDish get(String id){var root=root(id,false);return AdminDish.of(dishes.details(id,root.version()),root.published(),root.media());}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdminDishPage page(List<Category> categories,String search,Boolean published,int page,int size) {
        var filter=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if(!categories.isEmpty()){filter.append(" AND category IN (").append(String.join(",",Collections.nCopies(categories.size(),"?"))).append(")");categories.forEach(c->args.add(c.value()));}
        if(published!=null){filter.append(" AND is_listed=?");args.add(published);}
        if(!search.isEmpty()){String value="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";filter.append(" AND (name LIKE ? ESCAPE '!' OR description LIKE ? ESCAPE '!')");args.add(value);args.add(value);}
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM dishes"+filter,Long.class,args.toArray());args.add(size);args.add((long)page*size);var ids=jdbc.query("SELECT id FROM dishes"+filter+" ORDER BY name,id LIMIT ? OFFSET ?",(rs,n)->rs.getString(1),args.toArray());return new AdminDishPage(ids.stream().map(this::get).toList(),page,size,count);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public AdminDish create(UserIdentity actor,DishCreate body){return createWithId(actor,UUID.randomUUID().toString(),body);}
    @Transactional(isolation=Isolation.READ_COMMITTED) public AdminDish createWithId(UserIdentity actor,String id,DishCreate body) {
        admin(actor);ValueRules.uuid(id);if(body==null || body.optionGroups()==null)throw new IllegalArgumentException("Complete the dish fields.");
        var groups=new ArrayList<GroupWrite>();
        for(var group:body.optionGroups()) {if(group==null || group.options()==null)throw new IllegalArgumentException("Provide valid options.");String groupId=stableId(id+".group."+groups.size());var optionIndex=new java.util.concurrent.atomic.AtomicInteger();groups.add(new GroupWrite(groupId,group.name(),group.required(),group.selectionMode(),group.options().stream().map(option->{if(option==null)throw new IllegalArgumentException("Provide valid options.");return new OptionWrite(stableId(groupId+".option."+optionIndex.getAndIncrement()),option.name(),option.extraCents(),option.allergens());}).toList()));}
        var write=validated(new DishUpdate(body.name(),body.description(),body.basePriceCents(),body.category(),body.protein(),body.allergens(),body.spiceLevel(),body.mediaId(),groups,0L));
        Timestamp now=Timestamp.from(clock.now());jdbc.update("INSERT INTO dishes VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,write.name(),write.description(),write.basePriceCents(),write.category().value(),write.protein(),write.spiceLevel().name(),write.mediaId(),false,1,now,now);children(id,write);return get(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public AdminDish update(UserIdentity actor,String id,DishUpdate body) {
        admin(actor);var value=validated(body);var root=root(id,true);version(root.version(),value.expectedVersion());validateOwnership(id,value.optionGroups());
        jdbc.update("UPDATE dishes SET name=?,description=?,base_price_cents=?,category=?,protein_source=?,spice=?,media_id=?,version=?,updated_at=? WHERE id=?",value.name(),value.description(),value.basePriceCents(),value.category().value(),value.protein(),value.spiceLevel().name(),value.mediaId(),ValueRules.nextVersion(root.version()),Timestamp.from(clock.now()),id);
        // Current children are replaceable; immutable quotes/orders keep independent ID/name/price snapshots.
        jdbc.update("DELETE a FROM option_allergens a JOIN customization_options o ON o.id=a.option_id JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=?",id);
        jdbc.update("DELETE o FROM customization_options o JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=?",id);jdbc.update("DELETE FROM customization_groups WHERE dish_id=?",id);jdbc.update("DELETE FROM dish_allergens WHERE dish_id=?",id);children(id,value);changes.changed(id,actor.userId());return get(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public AdminDish publish(UserIdentity actor,String id,Publication body) {
        admin(actor);if(body==null || body.published()==null || body.expectedVersion()==null)throw new IllegalArgumentException("Publication and expected version are required.");ValueRules.safeInteger(body.expectedVersion());var root=root(id,true);version(root.version(),body.expectedVersion());
        jdbc.update("UPDATE dishes SET is_listed=?,version=?,updated_at=? WHERE id=?",body.published(),ValueRules.nextVersion(root.version()),Timestamp.from(clock.now()),id);changes.changed(id,actor.userId());return get(id);
    }
    private static String stableId(String value) {return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private DishUpdate validated(DishUpdate body) {
        if(body==null || body.basePriceCents()==null || body.category()==null || body.spiceLevel()==null || body.optionGroups()==null || body.expectedVersion()==null)throw new IllegalArgumentException("Complete the dish fields.");ValueRules.safeInteger(body.basePriceCents());ValueRules.safeInteger(body.expectedVersion());labels(body.allergens());String image=ValueRules.uuid(body.mediaId());if(media.find(image).isEmpty())throw ApiException.validation("mediaId","Choose a successfully uploaded image.");
        var groups=new ArrayList<GroupWrite>();var groupIds=new HashSet<String>();var optionIds=new HashSet<String>();
        for(var group:body.optionGroups()) {
            if(group==null || group.required()==null || group.selectionMode()==null || group.options()==null || group.options().isEmpty())throw new IllegalArgumentException("Each option group needs a selection mode and at least one option.");String groupId=group.groupId()==null?null:ValueRules.uuid(group.groupId());if(groupId!=null && !groupIds.add(groupId))throw new IllegalArgumentException("Duplicate option group.");var options=new ArrayList<OptionWrite>();
            for(var option:group.options()) {if(option==null || option.extraCents()==null)throw new IllegalArgumentException("Complete each option.");ValueRules.safeInteger(option.extraCents());labels(option.allergens());String optionId=option.optionId()==null?null:ValueRules.uuid(option.optionId());if(optionId!=null && !optionIds.add(optionId))throw new IllegalArgumentException("Duplicate option.");options.add(new OptionWrite(optionId,english(option.name(),100),option.extraCents(),List.copyOf(option.allergens())));}
            groups.add(new GroupWrite(groupId,english(group.name(),100),group.required(),group.selectionMode(),List.copyOf(options)));
        }
        return new DishUpdate(english(body.name(),100),english(body.description(),2000),body.basePriceCents(),body.category(),english(body.protein(),200),List.copyOf(body.allergens()),body.spiceLevel(),image,List.copyOf(groups),body.expectedVersion());
    }
    private void validateOwnership(String dish,List<GroupWrite> groups) {
        var ownedGroups=new HashSet<>(jdbc.queryForList("SELECT id FROM customization_groups WHERE dish_id=?",String.class,dish));var ownedOptions=new HashSet<>(jdbc.queryForList("SELECT o.id FROM customization_options o JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=?",String.class,dish));
        for(var group:groups) {if(group.groupId()!=null && !ownedGroups.contains(group.groupId()))throw new IllegalArgumentException("An option group does not belong to this dish.");for(var option:group.options())if(option.optionId()!=null && !ownedOptions.contains(option.optionId()))throw new IllegalArgumentException("An option does not belong to this dish.");}
    }
    private void children(String dish,DishUpdate value) {
        for(var label:value.allergens())jdbc.update("INSERT INTO dish_allergens VALUES (?,?)",dish,label.name());
        for(int gi=0;gi<value.optionGroups().size();gi++) {var group=value.optionGroups().get(gi);String groupId=group.groupId()==null?UUID.randomUUID().toString():group.groupId();jdbc.update("INSERT INTO customization_groups VALUES (?,?,?,?,?,?)",groupId,dish,group.name(),group.selectionMode().name(),group.required(),gi);
            for(int oi=0;oi<group.options().size();oi++) {var option=group.options().get(oi);String optionId=option.optionId()==null?UUID.randomUUID().toString():option.optionId();jdbc.update("INSERT INTO customization_options VALUES (?,?,?,?,?)",optionId,groupId,option.name(),option.extraCents(),oi);for(var label:option.allergens())jdbc.update("INSERT INTO option_allergens VALUES (?,?)",optionId,label.name());}
        }
    }
    private static void labels(List<Allergen> labels) {if(labels==null || labels.size()>7 || labels.stream().anyMatch(Objects::isNull) || new HashSet<>(labels).size()!=labels.size())throw new IllegalArgumentException("Use unique supported allergen labels.");}
    public static String english(String value,int max) {String text=ValueRules.requiredText(value,max);if(!text.matches("(?s).*[A-Za-z].*") || text.codePoints().anyMatch(c->Character.isLetter(c) && Character.UnicodeScript.of(c)!=Character.UnicodeScript.LATIN))throw new IllegalArgumentException("Use English text for menu information.");return text;}
    public static void admin(UserIdentity actor) {if(actor.role()!=Role.Admin)throw new ApiException(403,"FORBIDDEN","Administrator access is required.");}
    public static void version(long actual,long expected) {if(actual!=expected)throw new ApiException(409,"VERSION_CONFLICT","This record changed. Reload and review before saving.");}
}
