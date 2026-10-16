package com.webox.security;
import com.webox.support.AdminTestSupport;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import static org.junit.jupiter.api.Assertions.*;
/** Real TLS termination, production static bundle, secure sessions and unbuffered streams. */
@Tag("proxy")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test","prod"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ProductionProxyIT extends AdminTestSupport {
    private static final String IMAGE="nginx:1.30.6-alpine@sha256:970364228b1e4f932abc22d321932816782cca8a20b85a77552a7cfe9acfb3b5";
    @LocalServerPort int port;
    private Path tls;private GenericContainer<?> proxy;private HttpClient client;private String origin;
    @BeforeEach void startProxy()throws Exception {
        Path bundle=Path.of("../frontend/dist").toAbsolutePath().normalize();assertTrue(Files.isRegularFile(bundle.resolve("index.html")),"Build the real frontend before running -Pproxy verify.");tls=Files.createTempDirectory("webox-proxy-tls-");String password=UUID.randomUUID().toString();Path store=tls.resolve("local.p12");
        var keytool=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/keytool").toString(),"-genkeypair","-alias","localhost","-keyalg","RSA","-keysize","2048","-validity","1","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12","-keystore",store.toString(),"-storepass",password,"-noprompt").redirectErrorStream(true).redirectOutput(tls.resolve("keytool.log").toFile()).start();assertTrue(keytool.waitFor(20,TimeUnit.SECONDS));assertEquals(0,keytool.exitValue());
        var keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,password.toCharArray());}var certificate=keys.getCertificate("localhost");var privateKey=keys.getKey("localhost",password.toCharArray());writePem(tls.resolve("fullchain.pem"),"CERTIFICATE",certificate.getEncoded());writePem(tls.resolve("privkey.pem"),"PRIVATE KEY",privateKey.getEncoded());
        var trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("local",certificate);var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);var ssl=SSLContext.getInstance("TLS");ssl.init(null,factory.getTrustManagers(),new SecureRandom());client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(3)).version(HttpClient.Version.HTTP_1_1).build();
        String configuration=Files.readString(Path.of("../infra/nginx.conf")).replace("server 127.0.0.1:8080;","server host.testcontainers.internal:"+port+";");Files.writeString(tls.resolve("webox.conf"),configuration);Testcontainers.exposeHostPorts(port);
        proxy=new GenericContainer<>(IMAGE).withExposedPorts(443).withCopyFileToContainer(MountableFile.forHostPath(tls.resolve("webox.conf")),"/etc/nginx/conf.d/default.conf").withCopyFileToContainer(MountableFile.forHostPath(tls.resolve("fullchain.pem")),"/etc/nginx/tls/fullchain.pem").withCopyFileToContainer(MountableFile.forHostPath(tls.resolve("privkey.pem")),"/etc/nginx/tls/privkey.pem").withCopyFileToContainer(MountableFile.forHostPath(bundle),"/srv/webox/frontend").waitingFor(Wait.forListeningPort());proxy.start();origin="https://localhost:"+proxy.getMappedPort(443);
    }
    private void writePem(Path path,String type,byte[] bytes)throws IOException {Files.writeString(path,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)+"\n-----END "+type+"-----\n");}
    private HttpRequest.Builder request(String path){return HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(5));}
    private HttpResponse<String> get(String path)throws Exception {return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @AfterEach void stopProxy()throws Exception {if(proxy!=null)proxy.stop();if(tls!=null)try(var paths=Files.walk(tls)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    @Test void httpsServesTheBuiltSpaAndProxiesPrivateApiAndStreamsWithoutBuffering()throws Exception {
        var html=get("/console/dashboard");assertEquals(200,html.statusCode());assertTrue(html.body().contains("WeBox"));assertTrue(html.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));assertTrue(html.headers().firstValue("Strict-Transport-Security").orElseThrow().contains("max-age=31536000"));assertEquals(200,get("/api/v1/health").statusCode());assertEquals(401,get("/api/v1/auth/me").statusCode());assertEquals(401,get("/actuator/metrics").statusCode());assertEquals(404,get("/media/00000000-0000-4000-8000-000000000001").statusCode());assertEquals(404,get("/assets/missing.js").statusCode());
        var csrf=get("/api/v1/auth/csrf");String setCookie=csrf.headers().firstValue("Set-Cookie").orElseThrow();assertTrue(setCookie.contains("Secure"));assertTrue(setCookie.contains("HttpOnly"));assertTrue(setCookie.contains("SameSite=Lax"));
        var user=browser();String cookie="WEBOXSESSION="+user.identity().sessionId();long started=System.nanoTime();var stream=client.send(request("/api/v1/menus/2099-01-01/events").header("Cookie",cookie).GET().build(),HttpResponse.BodyHandlers.ofInputStream());assertEquals(200,stream.statusCode());assertTrue(stream.headers().firstValue("Content-Type").orElseThrow().contains("text/event-stream"));var reader=Executors.newSingleThreadExecutor();String event;
        try(var input=stream.body()){event=reader.submit(()->new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)).readLine()).get(2,TimeUnit.SECONDS);}finally{reader.shutdownNow();}long elapsed=(System.nanoTime()-started)/1_000_000;assertEquals("event: inventory.snapshot",event);assertTrue(elapsed<2000);
        var recommendation=client.send(request("/api/v1/recommendations/stream").header("Cookie",cookie).header("X-XSRF-TOKEN",user.csrf()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"deliveryDate\":\"2099-01-01\",\"meal\":\"Lunch\",\"prompt\":\"A meal.\"}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,recommendation.statusCode());assertTrue(recommendation.body().contains("event: meta"));assertTrue(recommendation.body().contains("NO_MATCH"));assertTrue(recommendation.body().contains("event: done"));
        Files.writeString(Path.of("target/production-proxy.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("nginxImage",IMAGE,"tls",true,"secureCookie",true,"builtSpa",true,"protectedMetrics",true,"inventorySnapshotMs",elapsed,"postSseNoMatch",true)));
    }
}
