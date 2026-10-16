package com.webox.shared;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Only server-authored messages and authorized context belong in public errors. */
public final class ApiException extends RuntimeException {
    private static final Set<String> CONTEXT_KEYS = Set.of("fieldErrors", "itemIssues", "existingOrderId", "suggestedDeliveryDate", "suggestedMeal");
    private final int status;
    private final String code;
    private final boolean retryable;
    private final Map<String,Object> context;
    public ApiException(int status, String code, String message) { this(status,code,message,false,Map.of()); }
    public ApiException(int status, String code, String message, boolean retryable, Map<String,Object> context) {
        super(message);
        if (!CONTEXT_KEYS.containsAll(context.keySet())) throw new IllegalArgumentException("Unsupported error context.");
        this.status=status; this.code=code; this.retryable=retryable; this.context=Map.copyOf(context);
    }
    public int status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
    public Map<String,Object> context() { return context; }
    public static ApiException validation(String field, String message) {
        return new ApiException(400,"VALIDATION_ERROR","Please check the highlighted fields.",false,
                Map.of("fieldErrors",List.of(Map.of("field",field,"message",message))));
    }
}
