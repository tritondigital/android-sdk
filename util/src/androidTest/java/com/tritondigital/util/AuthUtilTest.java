package com.tritondigital.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AuthUtilTest {

    static String JWT_HEADER      = "{\"typ\":\"JWT\",\"alg\":\"HS256\",\"kid\":\"a1b2c3d4e5\"}";
    static String ENCODED_HEADER  = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiIsImtpZCI6ImExYjJjM2Q0ZTUifQ";
    static String ENCODED_PAYLOAD = "eyJpc3MiOiJwZHZ5Iiwic3ViIjoiZm9vQGJhci5jb20iLCJpYXQiOjE0Mjk4MDI3MTYsInRkLXJlZyI6dHJ1ZX0";

    @Test
    public void createJwtHeader() {
        assertEquals(JWT_HEADER, AuthUtil.createJwtHeader("a1b2c3d4e5"));
        assertTrue(true);
    }

    @Test
    public void createEncodedJwsSignature() {
        String expected = "YeNcfr7Rcpv4P8Tu6Y2bRuGqYUGQM0lHjyK_nD8SWKA";
        String actual = AuthUtil.createEncodedJwsSignature(
                ENCODED_HEADER,
                ENCODED_PAYLOAD,
                "ThisIsASecretValue");
        assertEquals(expected, actual);
    }

    @Test
    public void base64UrlEncode_header() {
        assertEquals(ENCODED_HEADER, AuthUtil.base64UrlEncode(JWT_HEADER));
    }

    @Test
    public void base64UrlEncode_payload() {
        String actual = AuthUtil.base64UrlEncode("{\"iss\":\"pdvy\",\"sub\":\"foo@bar.com\",\"iat\":1429802716,\"td-reg\":true}");
        assertEquals(ENCODED_PAYLOAD, actual);
    }

    @Test
    public void base64UrlEncode_notLatin() {
        String expected = "eyAibXNnX2VuIjogIkhlbGxvIiwKICAibXNnX2pwIjogIuOBk-OCk-OBq-OBoeOBryIsCiAgIm1zZ19jbiI6ICLkvaDlpb0iLAogICJtc2dfa3IiOiAi7JWI64WV7ZWY7IS47JqUIiwKICAibXNnX3J1IjogItCX0LTRgNCw0LLRgdGC0LLRg9C50YLQtSEiLAogICJtc2dfZGUiOiAiR3LDvMOfIEdvdHQiIH0";
        String actual = AuthUtil.base64UrlEncode("{ \"msg_en\": \"Hello\",\n" +
                "  \"msg_jp\": \"こんにちは\",\n" +
                "  \"msg_cn\": \"你好\",\n" +
                "  \"msg_kr\": \"안녕하세요\",\n" +
                "  \"msg_ru\": \"Здравствуйте!\",\n" +
                "  \"msg_de\": \"Grüß Gott\" }");
        assertEquals(expected, actual);
    }
}
