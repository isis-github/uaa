/*
 * ******************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * ******************************************************************************
 */
package com.tianzhu.identity.uaa.provider.ldap;

import com.tianzhu.identity.uaa.user.DialableByPhone;
import com.tianzhu.identity.uaa.user.ExternallyIdentifiable;
import com.tianzhu.identity.uaa.user.Mailable;
import com.tianzhu.identity.uaa.user.Named;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import java.util.Map;

public interface ExtendedLdapUserDetails extends LdapUserDetails, Mailable, Named, DialableByPhone, ExternallyIdentifiable {

    String[] getMail();

    Map<String,String[]> getAttributes();

    String[] getAttribute(String name, boolean caseSensitive);

}
