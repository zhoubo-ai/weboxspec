package com.webox.shared;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
/** An omitted identifier means create a child; an explicit null is outside the API schema. */
public final class OptionalIdDeserializer extends ValueDeserializer<String> {
    @Override public String deserialize(JsonParser parser,DeserializationContext context) {return ValueRules.uuid(context.readValue(parser,String.class));}
    @Override public Object getNullValue(DeserializationContext context) {throw new IllegalArgumentException("Omit a new option identifier instead of sending null.");}
    @Override public Object getAbsentValue(DeserializationContext context) {return null;}
}
