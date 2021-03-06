package com.tianzhu.identity.uaa.oauth.token.matchers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tianzhu.identity.uaa.oauth.KeyInfo;
import com.tianzhu.identity.uaa.oauth.jwt.Jwt;
import com.tianzhu.identity.uaa.oauth.jwt.JwtHelper;
import com.tianzhu.identity.uaa.oauth.token.RevocableToken;
import com.tianzhu.identity.uaa.util.JsonUtils;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractOAuth2AccessTokenMatchers<T> extends TypeSafeMatcher<T> {

    protected Matcher<?> value;
    public static ThreadLocal<Map<String,RevocableToken>> revocableTokens = new ThreadLocal<Map<String,RevocableToken>>() {
        @Override
        protected Map<String,RevocableToken> initialValue() {
            return emptyMap();
        }
    };

    public AbstractOAuth2AccessTokenMatchers(Matcher<?> value) {
        this.value = value;
    }

    protected AbstractOAuth2AccessTokenMatchers() {
    }

    protected String getToken(String token) {
        if (revocableTokens.get().containsKey(token)) {
            return revocableTokens.get().get(token).getValue();
        } else {
            return token;
        }
    }

    @Override
    protected abstract boolean matchesSafely(T token);

    protected Map<String, Object> getClaims(T token) {
        String tokenValue = null;
        if (token instanceof OAuth2AccessToken)
            tokenValue = ((OAuth2AccessToken)token).getValue();
        else if (token instanceof OAuth2RefreshToken)
            tokenValue = ((OAuth2RefreshToken)token).getValue();
        else
            throw new IllegalArgumentException("token must be instanceof OAuth2AccessToken or OAuth2RefreshToken");

        Jwt tokenJwt = JwtHelper.decode(getToken(tokenValue));
        assertNotNull(tokenJwt);
        Map<String, Object> claims;
        try {
            claims = JsonUtils.readValue(tokenJwt.getClaims(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to decode token", e);
        }
        tokenJwt.verifySignature(KeyInfo.getKey(tokenJwt.getHeader().getKid()).getVerifier());
        return claims;
    }
}
