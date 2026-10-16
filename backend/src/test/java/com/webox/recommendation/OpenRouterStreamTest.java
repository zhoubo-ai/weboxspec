package com.webox.recommendation;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterStreamTest {
    private final JsonMapper json=JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final String id="10000000-0000-4000-8000-000000000001";
    @Test void innerJsonEmitsOnlyCompleteItemsAcrossEveryCharacterBoundary() {
        String text=json.writeValueAsString(Map.of("recommendations",List.of(Map.of("dishId",id,"reason","A \"mild\" choice with a literal brace } and Unicode café."),Map.of("dishId",id.replace("001","002"),"reason","A second choice."))));
        var emitted=new ArrayList<RecommendationModels.Generated>();var parser=new IncrementalRecommendationParser(json,emitted::add);
        for(int i=0;i<text.length();i++){parser.feed(text.substring(i,i+1));if(i<text.indexOf("},"))assertTrue(emitted.isEmpty());}
        assertEquals(2,emitted.size());assertEquals(emitted,parser.finish());assertTrue(emitted.get(0).reason().contains("café"));
    }
    @Test void malformedTruncatedDuplicateAndExtraFieldsNeverProduceSuccessfulCompletion() {
        String item=json.writeValueAsString(Map.of("dishId",id,"reason","A mild choice."));
        for(String text:List.of("", "{\"recommendations\":[", "{\"recommendations\":["+item, "{\"recommendations\":["+item+"]}garbage", "{\"recommendations\":["+item+"],\"other\":1}", "{\"recommendations\":[],\"recommendations\":[]}", "{\"recommendations\":[{\"dishId\":\""+id+"\",\"reason\":\"Fine\",\"price\":1}]}")) {
            var parser=new IncrementalRecommendationParser(json,unused->{});assertThrows(AiFailure.class,()->{parser.feed(text);parser.finish();});
        }
    }
    @Test void fullItemsMayPrecedeALaterFailureButIncompleteItemsAreNeverEmitted() {
        var emitted=new ArrayList<RecommendationModels.Generated>();var parser=new IncrementalRecommendationParser(json,emitted::add);
        parser.feed("{\"recommendations\":[{\"dishId\":\""+id+"\",\"reason\":\"A mild choice.\"},{\"dishId\":\"");assertEquals(1,emitted.size());assertThrows(AiFailure.class,parser::finish);
    }
    @Test void reasonValidationRejectsFabricatedCandidatesDuplicatesHtmlAndUnsupportedAssertions() {
        var seen=new HashSet<String>();var allowed=Set.of(id);RecommendationValidator.validate(new RecommendationModels.Generated(id,"This mild dish offers variety for your meal."),allowed,seen);
        assertThrows(AiFailure.class,()->RecommendationValidator.validate(new RecommendationModels.Generated(id,"Another reason."),allowed,seen));
        for(String reason:List.of("", "x".repeat(241),"<b>Mild</b>","[Click here](https://example.com)","This meal cures diabetes.","This dish is guaranteed allergen-free.","This contains 20 grams of protein.","这是一道好菜"))assertThrows(AiFailure.class,()->RecommendationValidator.validate(new RecommendationModels.Generated(id,reason),allowed,new HashSet<>()));
        assertThrows(AiFailure.class,()->RecommendationValidator.validate(new RecommendationModels.Generated(UUID.randomUUID().toString(),"A mild choice."),allowed,new HashSet<>()));
    }
    private String chunk(String content,String finish) {
        var choice=new LinkedHashMap<String,Object>();choice.put("index",0);choice.put("delta",Map.of("content",content));choice.put("finish_reason",finish);
        return "data: "+json.writeValueAsString(Map.of("id","test-generation","model","test-model","provider","Test","choices",List.of(choice)))+"\r\n\r\n";
    }
    @Test void outerSseHandlesUtf8CommentsCrossBlockContentAndRepeatedStopUsage() {
        String generated=json.writeValueAsString(Map.of("recommendations",List.of(Map.of("dishId",id,"reason","A café choice."))));
        String body=": provider comment\r\n\r\n"+chunk(generated.substring(0,15),null)+chunk(generated.substring(15),"stop")+"data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":42}}\r\n\r\ndata: [DONE]\r\n\r\n";
        var bytes=new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));var fragmented=new java.io.FilterInputStream(bytes){@Override public int read(byte[] b,int offset,int length){return bytes.read(b,offset,Math.min(length,1));}};
        var cards=new ArrayList<RecommendationModels.Generated>();try(var deadline=new RequestDeadline(java.time.Duration.ofSeconds(2))) {var result=OpenRouterProvider.parseStream(fragmented,json,deadline,cards::add);assertEquals(1,cards.size());assertEquals("A café choice.",cards.get(0).reason());assertEquals(42,result.metadata().usage().path("total_tokens").asInt());assertEquals("test-generation",result.metadata().generationId());}
    }
    @Test void providerErrorsTruncationAndMissingFinishMarkersCannotBecomeDone() {
        String generated=json.writeValueAsString(Map.of("recommendations",List.of(Map.of("dishId",id,"reason","A mild choice."))));
        for(String body:List.of(chunk(generated,"stop"),chunk(generated,null)+"data: [DONE]\n\n",chunk(generated,"length")+"data: [DONE]\n\n",chunk(generated,null)+"data: {\"error\":{\"message\":\"private upstream detail\"}}\n\n",chunk(generated,"stop")+"data: [DONE]\n\ndata: {}\n\n",chunk(generated.substring(0,30),"stop")+"data: [DONE]\n\n")) {
            try(var deadline=new RequestDeadline(java.time.Duration.ofSeconds(2))) {assertThrows(AiFailure.class,()->OpenRouterProvider.parseStream(new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),json,deadline,unused->{}));}
        }
    }
    @Test void sharedDeadlineExpiresAndCancellationClosesTrackedResources() throws Exception {
        var closed=new java.util.concurrent.CountDownLatch(1);try(var deadline=new RequestDeadline(java.time.Duration.ofMillis(50))) {deadline.track(closed::countDown);assertTrue(closed.await(1,java.util.concurrent.TimeUnit.SECONDS));assertEquals("AI_TIMEOUT",assertThrows(AiFailure.class,deadline::check).code());}
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();try(var deadline=new RequestDeadline(java.time.Duration.ofSeconds(2))) {deadline.track(()->cancelled.set(true));deadline.cancel();assertTrue(cancelled.get());assertThrows(java.util.concurrent.CancellationException.class,deadline::check);}
    }
}
