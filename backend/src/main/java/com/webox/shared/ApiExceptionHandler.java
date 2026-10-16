package com.webox.shared;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    private final OperationalMetrics metrics;
    public ApiExceptionHandler(OperationalMetrics metrics){this.metrics=metrics;}
    private static final Logger LOG=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void disconnected() {
        // Navigation can abort an image or stream response. Its closed output cannot accept an error body.
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,Object>> business(ApiException e,HttpServletRequest request) {
        metrics.failure(e.code());
        return ResponseEntity.status(e.status()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(ApiErrors.body(request,e));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class,HttpRequestMethodNotSupportedException.class,HttpMediaTypeNotSupportedException.class,org.springframework.web.bind.ServletRequestBindingException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception e,HttpServletRequest request) {
        return business(new ApiException(400,"VALIDATION_ERROR","The request contains invalid or unsupported fields."),request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> fields(MethodArgumentNotValidException e,HttpServletRequest request) {
        var errors=e.getBindingResult().getFieldErrors().stream().map(error->Map.of("field",error.getField(),"message","This field is invalid or required.")).toList();
        return business(new ApiException(400,"VALIDATION_ERROR","Please check the highlighted fields.",false,Map.of("fieldErrors",errors)),request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> missing(Exception e,HttpServletRequest request) {
        return business(new ApiException(404,"NOT_FOUND","The requested resource was not found."),request);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,Object>> upload(Exception e,HttpServletRequest request) {
        return business(new ApiException(413,"IMAGE_TOO_LARGE","The image exceeds the 5 MiB limit."),request);
    }
    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<Map<String,Object>> temporary(Exception e,HttpServletRequest request) {
        LOG.warn("requestId={} code=TEMPORARY_UNAVAILABLE type={}",ApiErrors.requestId(request),e.getClass().getSimpleName());
        return business(new ApiException(503,"TEMPORARY_UNAVAILABLE","The service is temporarily unavailable. Please retry.",true,Map.of()),request);
    }
    @ExceptionHandler({DataAccessException.class,Exception.class})
    public ResponseEntity<Map<String,Object>> unexpected(Exception e,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
        if(response.isCommitted()) {
            LOG.debug("requestId={} response=closed-or-committed type={}",ApiErrors.requestId(request),e.getClass().getSimpleName());
            return null;
        }
        // Exception messages and stacks can contain SQL parameters or private request content.
        LOG.error("requestId={} code=INTERNAL_ERROR type={}",ApiErrors.requestId(request),e.getClass().getSimpleName());
        return business(new ApiException(500,"INTERNAL_ERROR","Something went wrong. Please try again."),request);
    }
}
