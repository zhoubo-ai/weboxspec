package com.webox.shared;
import java.io.*;
import java.time.Clock;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;
@Configuration(proxyBeanMethods=false)
public class WebConfiguration {
    public static final int MAX_JSON_BYTES=1_048_576;
    @Bean org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer strictEnums() {
        return builder -> builder.enable(tools.jackson.databind.cfg.EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .withCoercionConfig(tools.jackson.databind.type.LogicalType.Textual, config -> {
                for (var shape : new tools.jackson.databind.cfg.CoercionInputShape[]{tools.jackson.databind.cfg.CoercionInputShape.Integer, tools.jackson.databind.cfg.CoercionInputShape.Float, tools.jackson.databind.cfg.CoercionInputShape.Boolean})
                    config.setCoercion(shape, tools.jackson.databind.cfg.CoercionAction.Fail);
            });
    }
    @Bean java.nio.file.Path mediaRoot(@org.springframework.beans.factory.annotation.Value("${webox.media-root}") String configured) throws IOException {
        var directory = java.nio.file.Path.of(configured).toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(directory);
        if (!java.nio.file.Files.isWritable(directory)) throw new IllegalStateException("The media directory is not writable.");
        return directory;
    }
    @Bean Clock applicationClock() { return Clock.systemUTC(); }
    @Bean JsonFactoryBuilderCustomizer jsonLimits() {
        return builder -> builder.streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(MAX_JSON_BYTES).maxNumberLength(32).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }
    @Bean FilterRegistrationBean<OncePerRequestFilter> requestBoundary(JsonMapper mapper) {
        var filter=new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
                response.setHeader("X-Request-Id",ApiErrors.requestId(request));
                if(request.getRequestURI().startsWith("/api/")) response.setHeader("Cache-Control","no-store");
                if(request.getContentType()!=null && (request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json") || request.getContentType().toLowerCase(java.util.Locale.ROOT).split(";",2)[0].endsWith("+json"))) {
                    if(request.getContentLengthLong()>MAX_JSON_BYTES) { tooLarge(mapper,request,response); return; }
                    byte[] bytes=request.getInputStream().readNBytes(MAX_JSON_BYTES+1);
                    if(bytes.length>MAX_JSON_BYTES) { tooLarge(mapper,request,response); return; }
                    var wrapped=new HttpServletRequestWrapper(request) {
                        @Override public ServletInputStream getInputStream() {
                            var input=new ByteArrayInputStream(bytes);
                            return new ServletInputStream() {
                                @Override public int read() { return input.read(); }
                                @Override public int read(byte[] b,int o,int l) { return input.read(b,o,l); }
                                @Override public boolean isFinished() { return input.available()==0; }
                                @Override public boolean isReady() { return true; }
                                @Override public void setReadListener(ReadListener listener) {
                                    try { if(!isFinished()) listener.onDataAvailable(); if(isFinished()) listener.onAllDataRead(); }
                                    catch(IOException e) { listener.onError(e); }
                                }
                            };
                        }
                        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
                    };
                    chain.doFilter(wrapped,response); return;
                }
                chain.doFilter(request,response);
            }
        };
        var bean=new FilterRegistrationBean<OncePerRequestFilter>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE+20); bean.setAsyncSupported(true); return bean;
    }
    private static void tooLarge(JsonMapper mapper,HttpServletRequest req,HttpServletResponse res) throws IOException {
        ApiErrors.write(mapper,req,res,new ApiException(400,"VALIDATION_ERROR","The JSON request is too large."));
    }
}
