/*
 * ****************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2017] Pivotal Software, Inc. All Rights Reserved.
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

package com.tianzhu.identity.uaa.provider.ldap;


import com.tianzhu.identity.uaa.provider.AbstractIdentityProviderDefinition;
import com.tianzhu.identity.uaa.provider.IdentityProvider;
import com.tianzhu.identity.uaa.provider.LdapIdentityProviderDefinition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatchers;

import static com.tianzhu.identity.uaa.constants.OriginKeys.LDAP;
import static org.mockito.Mockito.*;

public class LdapIdentityProviderConfigValidatorTest {


    @Rule
    public ExpectedException exception = ExpectedException.none();

    LdapIdentityProviderConfigValidator validator;

    @Before
    public void setup() {
        validator = spy(new LdapIdentityProviderConfigValidator());
    }

    @Test
    public void null_identity_provider() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Provider cannot be null");
        validator.validate((IdentityProvider<AbstractIdentityProviderDefinition>) null);
    }

    @Test
    public void invalid_ldap_origin() {
        IdentityProvider<LdapIdentityProviderDefinition> ldap = new IdentityProvider<>();
        ldap.setType(LDAP);
        ldap.setOriginKey("other");
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(String.format("LDAP provider originKey must be set to '%s'", LDAP));
        validator.validate(ldap);
    }


    @Test
    public void valid_ldap_origin() {
        IdentityProvider<LdapIdentityProviderDefinition> ldap = new IdentityProvider<>();
        ldap.setType(LDAP);
        ldap.setOriginKey(LDAP);
        doNothing().when(validator).validate(ArgumentMatchers.any(AbstractIdentityProviderDefinition.class));
        validator.validate(ldap);
        verify(validator, times(1)).validate((AbstractIdentityProviderDefinition) ArgumentMatchers.isNull());

    }
}