package com.webox.recommendation;
/** Only these bounded, public messages may cross the provider boundary. */
public final class AiFailure extends RuntimeException {
    private final String code;private final boolean retryable;
    public AiFailure(String code,String message,boolean retryable){super(message);this.code=code;this.retryable=retryable;}
    public String code(){return code;}public boolean retryable(){return retryable;}
    public static AiFailure invalid(){return new AiFailure("AI_INVALID_RESPONSE","The recommendation service returned an invalid result. Please retry or return to the menu.",true);}
    public static AiFailure unavailable(){return new AiFailure("AI_UNAVAILABLE","Recommendations are temporarily unavailable. You can still order from the menu.",true);}
    public static AiFailure timeout(){return new AiFailure("AI_TIMEOUT","Recommendations took too long. Please retry or return to the menu.",true);}
    public static AiFailure clarification(){return new AiFailure("INTENT_CLARIFICATION_REQUIRED","Please clarify the dates or restrictions you want to apply, then try again.",false);}
}
