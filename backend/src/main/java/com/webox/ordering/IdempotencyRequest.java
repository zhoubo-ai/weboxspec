package com.webox.ordering;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import com.webox.shared.ValueRules;
/** The stable request identity never depends on mutable address rows, quote expiry or prices. */
public record IdempotencyRequest(String key,String quoteId,String addressId,String addressText,String hash) {
    public static IdempotencyRequest normalize(String key,OrderingModels.OrderCreate body) {
        if(body==null || (body.addressId()==null)==(body.addressText()==null)) throw new IllegalArgumentException("Choose a saved address or enter a new address.");
        String quote=ValueRules.uuid(body.quoteId()),id=body.addressId()==null?null:ValueRules.uuid(body.addressId()),text=body.addressText()==null?null:ValueRules.requiredText(body.addressText(),200);
        // Length prefixes make the fields unambiguous even when an address contains delimiters.
        String address=id==null?text:id,kind=id==null?"text":"id";
        return new IdempotencyRequest(ValueRules.uuid(key),quote,id,text,digest("webox-order-v1|"+quote+"|"+kind+"|"+address.length()+":"+address));
    }
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is required",e); }
    }
    @Override public String toString() { return "IdempotencyRequest[redacted]"; }
}
