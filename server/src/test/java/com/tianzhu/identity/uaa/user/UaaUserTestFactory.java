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
package com.tianzhu.identity.uaa.user;

import com.tianzhu.identity.uaa.constants.OriginKeys;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;

import java.util.Date;

/**
 * @author Dave Syer
 *
 */
public class UaaUserTestFactory {

    public static UaaUser getUser(String id, String name, String email, String givenName, String familyName) {
        return new UaaUser(id, name, "", email, UaaAuthority.USER_AUTHORITIES, givenName, familyName, new Date(),
                        new Date(), OriginKeys.UAA, "externalId", false, IdentityZoneHolder.get().getId(), id, new Date());
    }

    public static UaaUser getAdminUser(String id, String name, String email, String givenName, String familyName) {
        return new UaaUser(id, name, "", email, UaaAuthority.ADMIN_AUTHORITIES, givenName, familyName, new Date(),
                        new Date(), OriginKeys.UAA, "externalId", false, IdentityZoneHolder.get().getId(), id, new Date());
    }

}
