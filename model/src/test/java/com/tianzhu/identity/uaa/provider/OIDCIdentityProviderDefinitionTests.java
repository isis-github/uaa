/*
 * *****************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2017] Pivotal Software, Inc. All Rights Reserved.
 *
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */

package com.tianzhu.identity.uaa.provider;

import com.tianzhu.identity.uaa.util.JsonUtils;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class OIDCIdentityProviderDefinitionTests {

    private final String defaultJson = "{\"emailDomain\":null,\"additionalConfiguration\":null,\"providerDescription\":null,\"externalGroupsWhitelist\":[],\"attributeMappings\":{},\"addShadowUserOnLogin\":true,\"storeCustomAttributes\":false,\"authUrl\":null,\"tokenUrl\":null,\"tokenKeyUrl\":null,\"tokenKey\":null,\"linkText\":null,\"showLinkText\":true,\"skipSslValidation\":false,\"relyingPartyId\":null,\"relyingPartySecret\":null,\"scopes\":null,\"issuer\":null,\"responseType\":\"code\",\"userInfoUrl\":null}";
    String url = "https://accounts.google.com/.well-known/openid-configuration";

    @Test
    public void serialize_discovery_url() throws MalformedURLException {
        OIDCIdentityProviderDefinition def = JsonUtils.readValue(defaultJson, OIDCIdentityProviderDefinition.class);
        assertNull(def.getDiscoveryUrl());
        def.setDiscoveryUrl(new URL(url));
        assertEquals(url, def.getDiscoveryUrl().toString());
        String json = JsonUtils.writeValueAsString(def);
        def = JsonUtils.readValue(json, OIDCIdentityProviderDefinition.class);
        assertEquals(url, def.getDiscoveryUrl().toString());
    }

}