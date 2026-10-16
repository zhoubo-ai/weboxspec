package com.webox.auth;
import com.webox.shared.ApiErrors;
import com.webox.shared.ApiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.session.web.http.DefaultCookieSerializer;
import tools.jackson.databind.json.JsonMapper;
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Configuration(proxyBeanMethods=false)
public class SecurityConfiguration {
    @Bean HttpSessionCsrfTokenRepository csrfRepository() {
        var repo=new HttpSessionCsrfTokenRepository(); repo.setHeaderName("X-XSRF-TOKEN"); return repo;
    }
    @Bean HttpSessionSecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean UserDetailsService disabledDefaultUser() { return name -> { throw new UsernameNotFoundException("Use the application login endpoint."); }; }
    @Bean DefaultCookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure}") boolean secure) {
        var cookie=new DefaultCookieSerializer();cookie.setCookieName("WEBOXSESSION");cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);cookie.setUseSecureCookie(secure);cookie.setSameSite("Lax");cookie.setUseBase64Encoding(false);return cookie;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<com.webox.shared.RateLimitFilter> rateLimitRegistration(com.webox.shared.RateLimitFilter filter) { var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration; }
    @Bean SecurityFilterChain security(HttpSecurity http,JsonMapper mapper,HttpSessionCsrfTokenRepository csrf,HttpSessionSecurityContextRepository contexts,com.webox.shared.RateLimitFilter limits) throws Exception {
        http.formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable()).requestCache(c->c.disable());
        http.securityContext(c->c.securityContextRepository(contexts).requireExplicitSave(true));
        http.csrf(c->c.csrfTokenRepository(csrf));
        http.addFilterAfter(limits,org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.authorizeHttpRequests(c->c
            // Only the container's internal error endpoint; normal /error and all API requests keep their rules.
            .requestMatchers(request->(request.getDispatcherType()==jakarta.servlet.DispatcherType.ERROR
                    || request.getDispatcherType()==jakarta.servlet.DispatcherType.INCLUDE
                        && request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION)!=null)
                && (request.getRequestURI().equals(request.getContextPath()+"/error")
                    || (request.getContextPath()+"/error").equals(request.getAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI)))).permitAll()
            .requestMatchers(HttpMethod.GET,"/api/v1/health","/api/v1/auth/csrf","/media/*").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET,"/actuator/health","/actuator/metrics","/actuator/metrics/**").hasRole("Admin")
            .requestMatchers("/api/v1/admin/**").hasRole("Admin")
            .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll());
        http.exceptionHandling(c->c
            .authenticationEntryPoint((req,res,e)->ApiErrors.write(mapper,req,res,new ApiException(401,"UNAUTHENTICATED","Please sign in to continue.")))
            .accessDeniedHandler((req,res,e)->ApiErrors.write(mapper,req,res,e instanceof CsrfException
                ? new ApiException(403,"CSRF_INVALID","Your security token is invalid. Please refresh and try again.")
                : new ApiException(403,"FORBIDDEN","You do not have permission to perform this action."))));
        return http.build();
    }
}
