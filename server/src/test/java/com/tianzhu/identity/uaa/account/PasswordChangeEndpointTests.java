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
package com.tianzhu.identity.uaa.account;

import com.tianzhu.identity.uaa.resources.jdbc.JdbcPagingListFactory;
import com.tianzhu.identity.uaa.resources.jdbc.LimitSqlAdapterFactory;
import com.tianzhu.identity.uaa.scim.ScimUser;
import com.tianzhu.identity.uaa.scim.exception.InvalidPasswordException;
import com.tianzhu.identity.uaa.scim.exception.ScimException;
import com.tianzhu.identity.uaa.scim.jdbc.JdbcScimUserProvisioning;
import com.tianzhu.identity.uaa.scim.test.TestUtils;
import com.tianzhu.identity.uaa.scim.validate.PasswordValidator;
import com.tianzhu.identity.uaa.security.SecurityContextAccessor;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class PasswordChangeEndpointTests {

    private ScimUser joel;

    private ScimUser dale;

    private PasswordChangeEndpoint endpoints;

    private static EmbeddedDatabase database;
    private static Flyway flyway;

    @BeforeClass
    public static void init() {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        database = builder.build();
        flyway = new Flyway();
        flyway.setBaselineVersion(MigrationVersion.fromVersion("1.5.2"));
        flyway.setLocations("classpath:/com/tianzhu/identity/uaa/db/hsqldb/");
        flyway.setDataSource(database);
        flyway.migrate();
    }

    @Before
    public void setup() {

        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        JdbcScimUserProvisioning dao = new JdbcScimUserProvisioning(jdbcTemplate,
                        new JdbcPagingListFactory(jdbcTemplate, LimitSqlAdapterFactory.getLimitSqlAdapter()));
        dao.setPasswordEncoder(NoOpPasswordEncoder.getInstance());

        endpoints = new PasswordChangeEndpoint();
        endpoints.setScimUserProvisioning(dao);

        joel = new ScimUser(null, "jdsa", "Joel", "D'sa");
        joel.addEmail("jdsa@vmware.com");
        dale = new ScimUser(null, "olds", "Dale", "Olds");
        dale.addEmail("olds@vmware.com");
        joel = dao.createUser(joel, "password", IdentityZoneHolder.get().getId());
        dale = dao.createUser(dale, "password", IdentityZoneHolder.get().getId());

    }

    @After
    public void clean() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        if (joel != null) {
            jdbcTemplate.update("delete from users where id=?", joel.getId());
        }
        if (dale != null) {
            jdbcTemplate.update("delete from users where id=?", dale.getId());
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        TestUtils.deleteFrom(database, "users", "groups", "group_membership");
        if (database != null) {
            database.shutdown();
        }
    }

    private SecurityContextAccessor mockSecurityContext(ScimUser user) {
        SecurityContextAccessor sca = mock(SecurityContextAccessor.class);
        String id = user.getId();
        when(sca.getUserId()).thenReturn(id);
        return sca;
    }

    @Test
    public void userCanChangeTheirOwnPasswordIfTheySupplyCorrectCurrentPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setOldPassword("password");
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test
    public void passwordIsValidated() throws Exception {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordValidator mockPasswordValidator = mock(PasswordValidator.class);
        endpoints.setPasswordValidator(mockPasswordValidator);
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setOldPassword("password");
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
        verify(mockPasswordValidator).validate("newpassword");
    }

    @Test(expected = ScimException.class)
    public void userCantChangeAnotherUsersPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setOldPassword("password");
        change.setPassword("newpassword");
        endpoints.changePassword(dale.getId(), change);
    }

    @Test
    public void adminCanChangeAnotherUsersPassword() {
        SecurityContextAccessor sca = mockSecurityContext(dale);
        when(sca.isAdmin()).thenReturn(true);
        endpoints.setSecurityContextAccessor(sca);
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test(expected = ScimException.class)
    public void changePasswordRequestFailsForUserWithoutCurrentPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test(expected = ScimException.class)
    public void changePasswordRequestFailsForAdminWithoutOwnCurrentPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test
    public void clientCanChangeUserPasswordWithoutCurrentPassword() {
        SecurityContextAccessor sca = mockSecurityContext(joel);
        when(sca.isClient()).thenReturn(true);
        endpoints.setSecurityContextAccessor(sca);
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("newpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test(expected = BadCredentialsException.class)
    public void changePasswordFailsForUserIfTheySupplyWrongCurrentPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("newpassword");
        change.setOldPassword("wrongpassword");
        endpoints.changePassword(joel.getId(), change);
    }

    @Test
    public void changePasswordFailsForNewPasswordIsSameAsCurrentPassword() {
        endpoints.setSecurityContextAccessor(mockSecurityContext(joel));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setPassword("password");
        change.setOldPassword("password");
        try {
            endpoints.changePassword(joel.getId(), change);
            fail();
        } catch (InvalidPasswordException e) {
            assertEquals("Your new password cannot be the same as the old password.", e.getLocalizedMessage());
        }
    }

}
