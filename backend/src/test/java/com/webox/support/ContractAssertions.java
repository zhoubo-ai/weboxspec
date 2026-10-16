package com.webox.support;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.InputFormat;
import static org.junit.jupiter.api.Assertions.*;
/** Reads the authoritative OpenAPI document, including nested $refs and format constraints. */
public final class ContractAssertions {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Map<String,Object> API=readContract();
    private static final SchemaRegistry REGISTRY=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private ContractAssertions() {}
    private static Map<String,Object> readContract() {
        try(var in=ContractAssertions.class.getResourceAsStream("/contracts/openapi.yaml")) {
            if(in==null) throw new IllegalStateException("OpenAPI contract is missing.");
            return new Yaml().load(in);
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static void matches(String schemaName,String body) {
        var wrapper=new LinkedHashMap<String,Object>();wrapper.put("$ref","#/components/schemas/"+schemaName);wrapper.put("components",API.get("components"));
        assertSchema(wrapper,body);
    }
    @SuppressWarnings("unchecked")
    public static void response(String path,String method,int status,String body) {
        Map<String,Object> paths=(Map<String,Object>)API.get("paths");
        Map<String,Object> item=(Map<String,Object>)paths.get(path);
        Map<String,Object> operation=(Map<String,Object>)item.get(method);
        Map<String,Object> responses=(Map<String,Object>)operation.get("responses");
        Map<String,Object> response=(Map<String,Object>)responses.get(String.valueOf(status));
        assertNotNull(response,"Response status must be declared in OpenAPI");
        Map<String,Object> content=(Map<String,Object>)response.get("content");
        Map<String,Object> media=(Map<String,Object>)content.get("application/json");
        var wrapper=new LinkedHashMap<>((Map<String,Object>)media.get("schema"));wrapper.put("components",API.get("components"));
        assertSchema(wrapper,body);
    }
    private static void assertSchema(Map<String,Object> schema,String body) {
        var compiled=REGISTRY.getSchema(JSON.writeValueAsString(schema),InputFormat.JSON);
        var errors=compiled.validate(body,InputFormat.JSON,ctx->ctx.executionConfig(config->config.formatAssertionsEnabled(true)));
        assertTrue(errors.isEmpty(),()->"Response violates the declared schema: "+errors);
    }
}
