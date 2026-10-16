package com.webox.recommendation;
import java.util.*;
import java.util.function.Consumer;
import tools.jackson.databind.json.JsonMapper;
import static com.webox.recommendation.RecommendationModels.*;

/** Incremental JSON grammar, including quoted strings and escapes; never searches braces with a regex. */
public final class IncrementalRecommendationParser {
    private enum State { ROOT, KEY_START, KEY, COLON, ARRAY, FIRST, ITEM, AFTER_ITEM, NEXT_ITEM, ROOT_END, END }
    private final JsonMapper json;private final Consumer<Generated> consumer;private final List<Generated> emitted=new ArrayList<>();
    private final StringBuilder all=new StringBuilder(),part=new StringBuilder();private State state=State.ROOT;private int depth;private boolean quoted,escape;
    public IncrementalRecommendationParser(JsonMapper json,Consumer<Generated> consumer){this.json=json;this.consumer=consumer;}
    public void feed(String chunk) {
        if(chunk==null || all.length()+chunk.length()>65536)throw AiFailure.invalid();all.append(chunk);
        for(int i=0;i<chunk.length();i++)accept(chunk.charAt(i));
    }
    private void accept(char c) {
        if(state==State.ITEM) {
            part.append(c);
            if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;return;}
            if(c=='"'){quoted=true;return;}
            if(c=='{' || c=='['){if(++depth>20)throw AiFailure.invalid();}
            else if(c=='}' || c==']') {
                if(--depth==0) {
                    Generated value=parseItem(part.toString());if(emitted.size()>=3)throw AiFailure.invalid();emitted.add(value);consumer.accept(value);part.setLength(0);state=State.AFTER_ITEM;
                }
            }
            return;
        }
        if(state==State.KEY) {
            part.append(c);if(escape){escape=false;return;}if(c=='\\'){escape=true;return;}if(c=='"') {
                try{if(!"recommendations".equals(json.readValue(part.toString(),String.class)))throw AiFailure.invalid();}catch(RuntimeException invalid){throw AiFailure.invalid();}
                part.setLength(0);state=State.COLON;
            }return;
        }
        if(c==' ' || c=='\n' || c=='\r' || c=='\t')return;
        switch(state) {
            case ROOT -> {require(c,'{');state=State.KEY_START;}
            case KEY_START -> {require(c,'"');part.append(c);state=State.KEY;}
            case COLON -> {require(c,':');state=State.ARRAY;}
            case ARRAY -> {require(c,'[');state=State.FIRST;}
            case FIRST -> {if(c==']')state=State.ROOT_END;else startItem(c);}
            case NEXT_ITEM -> startItem(c);
            case AFTER_ITEM -> {if(c==',')state=State.NEXT_ITEM;else {require(c,']');state=State.ROOT_END;}}
            case ROOT_END -> {require(c,'}');state=State.END;}
            default -> throw AiFailure.invalid();
        }
    }
    private static void require(char value,char expected){if(value!=expected)throw AiFailure.invalid();}
    private void startItem(char c){require(c,'{');part.append(c);depth=1;quoted=false;escape=false;state=State.ITEM;}
    private Generated parseItem(String source){try {var value=json.readValue(source,Generated.class);if(value==null || value.dishId()==null || value.reason()==null)throw AiFailure.invalid();return value;}catch(RuntimeException invalid){throw AiFailure.invalid();}}
    public List<Generated> finish() {
        if(state!=State.END)throw AiFailure.invalid();
        try {var value=json.readValue(all.toString(),Output.class);if(value==null || value.recommendations()==null || !value.recommendations().equals(emitted))throw AiFailure.invalid();return List.copyOf(emitted);}catch(RuntimeException invalid){throw AiFailure.invalid();}
    }
}
