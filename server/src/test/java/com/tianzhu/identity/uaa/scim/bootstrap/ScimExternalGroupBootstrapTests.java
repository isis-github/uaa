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
package com.tianzhu.identity.uaa.scim.bootstrap;

import com.tianzhu.identity.uaa.constants.OriginKeys;
import com.tianzhu.identity.uaa.resources.jdbc.JdbcPagingListFactory;
import com.tianzhu.identity.uaa.scim.ScimGroup;
import com.tianzhu.identity.uaa.scim.ScimGroupExternalMembershipManager;
import com.tianzhu.identity.uaa.scim.jdbc.JdbcScimGroupExternalMembershipManager;
import com.tianzhu.identity.uaa.scim.jdbc.JdbcScimGroupProvisioning;
import com.tianzhu.identity.uaa.test.JdbcTestBase;
import com.tianzhu.identity.uaa.zone.IdentityZone;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ScimExternalGroupBootstrapTests extends JdbcTestBase {

    private JdbcScimGroupProvisioning gDB;

    private ScimGroupExternalMembershipManager eDB;

    private ScimExternalGroupBootstrap bootstrap;

    @Before
    public void initScimExternalGroupBootstrapTests() {
        JdbcPagingListFactory pagingListFactory = new JdbcPagingListFactory(jdbcTemplate, limitSqlAdapter);
        gDB = new JdbcScimGroupProvisioning(jdbcTemplate, pagingListFactory);
        eDB = new JdbcScimGroupExternalMembershipManager(jdbcTemplate);
        ((JdbcScimGroupExternalMembershipManager) eDB).setScimGroupProvisioning(gDB);
        assertEquals(0, gDB.retrieveAll(IdentityZoneHolder.get().getId()).size());

        gDB.create(new ScimGroup(null, "acme", IdentityZone.getUaa().getId()), IdentityZoneHolder.get().getId());
        gDB.create(new ScimGroup(null, "acme.dev", IdentityZone.getUaa().getId()), IdentityZoneHolder.get().getId());

        bootstrap = new ScimExternalGroupBootstrap(gDB, eDB);
    }

    @Test
    public void canAddExternalGroups() throws Exception {
        Map<String, Map<String, List>> originMap = new HashMap<>();
        Map<String, List> externalGroupMap = new HashMap<>();
        externalGroupMap.put("cn=Engineering Department,ou=groups,dc=example,dc=com", Arrays.asList("acme", "acme.dev"));
        externalGroupMap.put("cn=HR,ou=groups,dc=example,dc=com", Collections.singletonList("acme"));
        externalGroupMap.put("cn=mgmt,ou=groups,dc=example,dc=com", Collections.singletonList("acme"));
        originMap.put(OriginKeys.LDAP, externalGroupMap);
        bootstrap.setExternalGroupMaps(originMap);
        bootstrap.afterPropertiesSet();

        assertEquals(2, eDB.getExternalGroupMapsByExternalGroup("cn=Engineering Department,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
        assertEquals(1, eDB.getExternalGroupMapsByExternalGroup("cn=HR,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
        assertEquals(1, eDB.getExternalGroupMapsByExternalGroup("cn=mgmt,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());

        assertEquals(3, eDB.getExternalGroupMapsByGroupName("acme", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
        assertEquals(1, eDB.getExternalGroupMapsByGroupName("acme.dev", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
    }

    @Test
    public void cannotAddExternalGroupsThatDoNotExist() throws Exception {
        Map<String, Map<String, List>> originMap = new HashMap<>();
        Map<String, List> externalGroupMap = new HashMap<>();
        externalGroupMap.put("cn=Engineering Department,ou=groups,dc=example,dc=com", Arrays.asList("acme", "acme.dev"));
        externalGroupMap.put("cn=HR,ou=groups,dc=example,dc=com", Collections.singletonList("acme"));
        externalGroupMap.put("cn=mgmt,ou=groups,dc=example,dc=com", Collections.singletonList("acme"));
        originMap.put(OriginKeys.UAA, externalGroupMap);
        bootstrap.setExternalGroupMaps(originMap);
        bootstrap.afterPropertiesSet();

        assertEquals(0, eDB.getExternalGroupMapsByExternalGroup("cn=Engineering Department,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
        assertEquals(0, eDB.getExternalGroupMapsByExternalGroup("cn=HR,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
        assertEquals(0, eDB.getExternalGroupMapsByExternalGroup("cn=mgmt,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());

        assertNull(eDB.getExternalGroupMapsByGroupName("acme1", OriginKeys.LDAP, IdentityZoneHolder.get().getId()));
        assertNull(eDB.getExternalGroupMapsByGroupName("acme1.dev", OriginKeys.LDAP, IdentityZoneHolder.get().getId()));
    }

    @Test
    public void cannotAddExternalGroupsThatMapToNull() throws Exception {
        Map<String, Map<String, List>> originMap = new HashMap<>();
        Map<String, List> externalGroupMap = new HashMap<>();
        externalGroupMap.put("cn=Engineering Department,ou=groups,dc=example,dc=com", null);
        originMap.put(OriginKeys.LDAP, externalGroupMap);
        bootstrap.setExternalGroupMaps(originMap);
        bootstrap.afterPropertiesSet();

        assertEquals(0, eDB.getExternalGroupMapsByExternalGroup("cn=Engineering Department,ou=groups,dc=example,dc=com", OriginKeys.LDAP, IdentityZoneHolder.get().getId()).size());
    }

    @Test
    public void cannotAddOriginMapToNull() throws Exception {
        Map<String, Map<String, List>> originMap = new HashMap<>();
        originMap.put(OriginKeys.LDAP, null);
        bootstrap.setExternalGroupMaps(originMap);
        bootstrap.afterPropertiesSet();
    }
}
