/*
 * ****************************************************************************
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
 * ****************************************************************************
 */

package com.tianzhu.identity.uaa.oauth;

import com.tianzhu.identity.uaa.zone.ClientServicesExtension;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.common.exceptions.RedirectMismatchException;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.oauth2.provider.NoSuchClientException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.oauth2.provider.endpoint.RedirectResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.util.StringUtils.hasText;

@RunWith(Parameterized.class)
public class AuthorizePromptNoneEntryPointTest {

    private static final String REDIRECT_URI = "http://sub.domain.com/callback?oauth=true";
    private static final String HTTP_SOME_OTHER_SITE_CALLBACK = "http://some.other.site/callback";
    private final SessionAuthenticationException authException = new SessionAuthenticationException("");
    private AuthenticationFailureHandler failureHandler;
    private AuthenticationEntryPoint entryPoint;
    private BaseClientDetails client;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ClientServicesExtension clientDetailsService;
    private RedirectResolver redirectResolver;

    private final String responseType;
    private final String redirectHash;
    private final String redirectUrl;

    public AuthorizePromptNoneEntryPointTest(String responseType, String redirectHash) {
        this.responseType = responseType;
        this.redirectHash = redirectHash;
        redirectUrl = REDIRECT_URI + redirectHash;
    }

    @Parameterized.Parameters(name = "{index}: {0} {1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {"code", ""},
            {"token", ""},
            {"id_token", ""},
            {"token id_token", ""},
            {"code", "#frag"},
            {"token", "#frag"},
            {"id_token", "#frag"},
            {"token id_token", "#frag"}
        });
    }

    @Before
    public void setup() {
        client = new BaseClientDetails("id", "", "openid", "authorization_code", "", redirectUrl);
        clientDetailsService = mock(ClientServicesExtension.class);
        redirectResolver = mock(RedirectResolver.class);
        failureHandler = mock(AuthenticationFailureHandler.class);

        String zoneID = IdentityZoneHolder.get().getId();
        when(clientDetailsService.loadClientByClientId(ArgumentMatchers.eq(client.getClientId()), ArgumentMatchers.eq(zoneID))).thenReturn(client);
        when(redirectResolver.resolveRedirect(ArgumentMatchers.eq(redirectUrl), ArgumentMatchers.same(client))).thenReturn(redirectUrl);
        when(redirectResolver.resolveRedirect(ArgumentMatchers.eq(HTTP_SOME_OTHER_SITE_CALLBACK), ArgumentMatchers.same(client))).thenThrow(new RedirectMismatchException(""));

        entryPoint = new AuthorizePromptNoneEntryPoint(failureHandler, clientDetailsService, redirectResolver);

        request = new MockHttpServletRequest("GET", "/oauth/authorize");
        request.setParameter(OAuth2Utils.CLIENT_ID, client.getClientId());
        request.setParameter(OAuth2Utils.RESPONSE_TYPE, responseType);
        response = new MockHttpServletResponse();
    }

    @Test
    public void test_missing_client_id() throws Exception {
        request.removeParameter(OAuth2Utils.CLIENT_ID);
        entryPoint.commence(request, response, authException);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
    }

    @Test
    public void test_missing_redirect_uri() throws Exception {
        client.setRegisteredRedirectUri(Collections.emptySet());
        entryPoint.commence(request, response, authException);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
    }

    @Test
    public void test_client_not_found() throws Exception {
        reset(clientDetailsService);
        when(clientDetailsService.loadClientByClientId(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenThrow(new NoSuchClientException("not found"));
        entryPoint.commence(request, response, authException);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
    }

    @Test
    public void test_redirect_mismatch() throws Exception {
        request.setParameter(OAuth2Utils.REDIRECT_URI, HTTP_SOME_OTHER_SITE_CALLBACK);
        entryPoint.commence(request, response, authException);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
    }

    @Test
    public void test_redirect_contains_error() throws Exception {
        request.setParameter(OAuth2Utils.REDIRECT_URI, redirectUrl);
        entryPoint.commence(request, response, authException);
        assertEquals(HttpStatus.FOUND.value(), response.getStatus());
        String paramValue = "error=login_required";
        String expectedRedirect = REDIRECT_URI + (responseType.equals("code") ? "&" + paramValue + redirectHash : (hasText(redirectHash) ? redirectHash + "&" : "#") + paramValue);
        assertEquals(expectedRedirect, response.getHeader("Location"));
    }

    @Test
    public void test_failure_handler_is_invoked() throws Exception {
        request.setParameter(OAuth2Utils.REDIRECT_URI, redirectUrl);
        entryPoint.commence(request, response, authException);
        verify(failureHandler, times(1)).onAuthenticationFailure(ArgumentMatchers.same(request), ArgumentMatchers.same(response), ArgumentMatchers.same(authException));
    }



}