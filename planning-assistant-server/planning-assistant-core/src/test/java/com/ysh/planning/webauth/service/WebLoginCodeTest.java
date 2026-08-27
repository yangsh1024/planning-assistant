package com.ysh.planning.webauth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebLoginCodeTest {

    @Test
    void formatsFallbackCodesAsSixDigits() {
        assertEquals("000042", WebLoginCode.format(42));
    }
}
