/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package com.tianzhu.identity.uaa.oauth;

import com.tianzhu.identity.uaa.oauth.token.ClaimConstants;
import com.tianzhu.identity.uaa.util.JsonUtils;
import org.junit.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Dave Syer
 *
 */
public class RemoteTokenServicesTests {

    private RemoteTokenServices services = new RemoteTokenServices();

    private Map<String, Object> body = new HashMap<String, Object>();

    private HttpHeaders headers = new HttpHeaders();

    private HttpStatus status = HttpStatus.OK;

    public RemoteTokenServicesTests() {
        services.setClientId("client");
        services.setClientSecret("secret");
        body.put(ClaimConstants.CLIENT_ID, "remote");
        body.put(ClaimConstants.USER_NAME, "olds");
        body.put(ClaimConstants.EMAIL, "olds@vmware.com");
        body.put(ClaimConstants.ISS, "http://some.issuer.com");
        body.put(ClaimConstants.USER_ID, "HDGFJSHGDF");
        services.setRestTemplate(new RestTemplate() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                                  Class<T> responseType, Object... uriVariables) throws RestClientException {
                return new ResponseEntity<T>((T) body, headers, status);
            }
        });
    }

    @Test
    public void testTokenRetrieval() throws Exception {
        OAuth2Authentication result = services.loadAuthentication("FOO");
        assertNotNull(result);
        assertEquals("remote", result.getOAuth2Request().getClientId());
        assertEquals("olds", result.getUserAuthentication().getName());
        assertEquals("HDGFJSHGDF", ((RemoteUserAuthentication) result.getUserAuthentication()).getId());
        assertNotNull(result.getOAuth2Request().getRequestParameters());
        assertNull(result.getOAuth2Request().getRequestParameters().get(ClaimConstants.ISS));
    }

    @Test
    public void testTokenRetrievalWithClaims() throws Exception {
        services.setStoreClaims(true);
        OAuth2Authentication result = services.loadAuthentication("FOO");
        assertNotNull(result);
        assertEquals("remote", result.getOAuth2Request().getClientId());
        assertEquals("olds", result.getUserAuthentication().getName());
        assertEquals("HDGFJSHGDF", ((RemoteUserAuthentication) result.getUserAuthentication()).getId());
        assertNotNull(result.getOAuth2Request().getRequestParameters());
        assertNotNull(result.getOAuth2Request().getRequestParameters().get(ClaimConstants.ISS));
    }

    @Test
    public void testTokenRetrievalWithClientAuthorities() throws Exception {
        body.put("client_authorities", Collections.singleton("uaa.none"));
        OAuth2Authentication result = services.loadAuthentication("FOO");
        assertNotNull(result);
        assertEquals("[uaa.none]", result.getOAuth2Request().getAuthorities().toString());
    }

    @Test
    public void testTokenRetrievalWithUserAuthorities() throws Exception {
        body.put("user_authorities", Collections.singleton("uaa.user"));
        OAuth2Authentication result = services.loadAuthentication("FOO");
        assertNotNull(result);
        assertEquals("[uaa.user]", result.getUserAuthentication().getAuthorities().toString());
    }

    @Test
    public void testTokenRetrievalWithAdditionalAuthorizationAttributes() throws Exception {
        Map additionalAuthorizationAttributesMap = Collections.singletonMap("test", 1);
        body.put(ClaimConstants.ADDITIONAL_AZ_ATTR, additionalAuthorizationAttributesMap);

        OAuth2Authentication result = services.loadAuthentication("FOO");

        assertNotNull(result);
        assertEquals(JsonUtils.writeValueAsString(additionalAuthorizationAttributesMap), result.getOAuth2Request()
                        .getRequestParameters().get(ClaimConstants.ADDITIONAL_AZ_ATTR));
    }
}
