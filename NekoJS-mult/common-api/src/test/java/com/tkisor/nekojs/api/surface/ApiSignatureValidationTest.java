package com.tkisor.nekojs.api.surface;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiSignatureValidationTest {
    @Test
    void rejectsRequiredParameterAfterOptionalParameter() {
        assertThrows(IllegalArgumentException.class, () -> ApiSignature.function(
                List.of(
                        new ApiParameter("optional", ApiTypeRef.primitive("string"), true, false),
                        new ApiParameter("required", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.voidType()));
    }

    @Test
    void rejectsNonFinalVarargsParameter() {
        assertThrows(IllegalArgumentException.class, () -> ApiSignature.function(
                List.of(
                        new ApiParameter("values", ApiTypeRef.primitive("string"), false, true),
                        new ApiParameter("tail", ApiTypeRef.primitive("string"), true, false)),
                ApiTypeRef.voidType()));
    }
}
