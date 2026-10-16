package com.webox.recommendation;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/** Secrets are private fields, never a record, response or log value. */
@Component
public final class OpenRouterConfiguration {
    private final URI endpoint;private final String key,model;private final int maxTokens,concurrent,queue;private final long timeout;
    public OpenRouterConfiguration(@Value("${OPENROUTER_BASE_URL:https://openrouter.ai/api/v1}") String base,@Value("${OPENROUTER_API_KEY:}") String key,@Value("${OPENROUTER_MODEL:openai/gpt-4.1-mini}") String model,
        @Value("${AI_MAX_OUTPUT_TOKENS:512}") int maxTokens,@Value("${AI_MAX_CONCURRENT:1}") int concurrent,@Value("${AI_QUEUE_CAPACITY:8}") int queue,@Value("${AI_TOTAL_TIMEOUT_MS:20000}") long timeout,@Value("${webox.openrouter.allow-loopback:false}") boolean allowLoopback) {
        this.endpoint=URI.create(base.replaceAll("/+$","")+"/chat/completions");this.key=key.strip();this.model=model.strip();this.maxTokens=maxTokens;this.concurrent=concurrent;this.queue=queue;this.timeout=timeout;
        boolean local=allowLoopback && this.key.startsWith("test-") && "http".equals(endpoint.getScheme()) && java.util.Set.of("127.0.0.1","localhost").contains(endpoint.getHost());
        if((!"https".equals(endpoint.getScheme()) && !local) || endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getRawQuery()!=null || endpoint.getFragment()!=null || this.model.isEmpty() || this.model.length()>100 || maxTokens<64 || maxTokens>4096 || concurrent<1 || concurrent>16 || queue<1 || queue>128 || timeout<50 || timeout>20_000)throw new IllegalArgumentException("Invalid recommendation provider configuration.");
    }
    public boolean available(){return !key.isEmpty();}public URI endpoint(){return endpoint;}String authorization(){return "Bearer "+key;}
    public String model(){return model;}public int maxTokens(){return maxTokens;}public int concurrent(){return concurrent;}public int queue(){return queue;}public long timeoutMillis(){return timeout;}
}
