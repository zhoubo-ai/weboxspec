package com.webox.shared;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
public final class ApiErrors {
    public static final String REQUEST_ID = ApiErrors.class.getName()+".requestId";
    private ApiErrors() {}
    public static String requestId(HttpServletRequest request) {
        var value=request.getAttribute(REQUEST_ID);
        if (value instanceof String s) return s;
        String id=UUID.randomUUID().toString(); request.setAttribute(REQUEST_ID,id); return id;
    }
    public static Map<String,Object> body(HttpServletRequest request, ApiException error) {
        var result=new LinkedHashMap<String,Object>();
        result.put("code",error.code()); result.put("message",error.getMessage());
        result.put("requestId",requestId(request)); result.put("retryable",error.retryable()); result.putAll(error.context());
        return result;
    }
    public static void write(JsonMapper mapper,HttpServletRequest request,HttpServletResponse response,ApiException error) throws IOException {
        if(response.isCommitted())return;
        response.setStatus(error.status()); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Request-Id",requestId(request));
        response.getWriter().write(mapper.writeValueAsString(body(request,error)));
    }
}
