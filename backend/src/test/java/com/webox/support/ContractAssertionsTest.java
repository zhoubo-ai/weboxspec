package com.webox.support;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContractAssertionsTest {
    @Test void validatesRequiredFieldsNestedReferencesFormatsAndClosedObjects() {
        ContractAssertions.matches("User","{\"userId\":\"d65a8dfd-51f2-4009-88b0-ddd16a2c14de\",\"email\":\"a@example.com\",\"role\":\"Employee\"}");
        for (String body : new String[]{"{}","{\"userId\":\"not-uuid\",\"email\":\"a@example.com\",\"role\":\"Employee\"}","{\"userId\":\"d65a8dfd-51f2-4009-88b0-ddd16a2c14de\",\"email\":\"invalid\",\"role\":\"Employee\"}","{\"userId\":\"d65a8dfd-51f2-4009-88b0-ddd16a2c14de\",\"email\":\"a@example.com\",\"role\":\"Employee\",\"password\":\"unexpected\"}"})
            assertThrows(AssertionError.class,()->ContractAssertions.matches("User",body));
        ContractAssertions.matches("MoneyCents","9007199254740991");
        assertThrows(AssertionError.class,()->ContractAssertions.matches("MoneyCents","9007199254740992"));
    }
}
