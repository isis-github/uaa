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

import com.tianzhu.identity.uaa.authentication.UaaPrincipal;
import com.tianzhu.identity.uaa.user.UaaUser;
import com.tianzhu.identity.uaa.user.UaaUserDatabase;
import com.tianzhu.identity.uaa.user.UserInfo;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.expression.OAuth2ExpressionUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

import static com.tianzhu.identity.uaa.oauth.token.ClaimConstants.ROLES;
import static com.tianzhu.identity.uaa.oauth.token.ClaimConstants.USER_ATTRIBUTES;


/**
 * Controller that sends user info to clients wishing to authenticate.
 *
 * @author Dave Syer
 */
@Controller
public class UserInfoEndpoint implements InitializingBean {

    @Autowired
    @Qualifier("userDatabase")
    private UaaUserDatabase userDatabase;

    public void setUserDatabase(UaaUserDatabase userDatabase) {
        this.userDatabase = userDatabase;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.state(userDatabase != null, "A user database must be provided");
    }

    @RequestMapping(value = "/userinfo")
    @ResponseBody
    public UserInfoResponse loginInfo(Principal principal) {
        OAuth2Authentication authentication = (OAuth2Authentication) principal;
        UaaPrincipal uaaPrincipal = extractUaaPrincipal(authentication);
        boolean addCustomAttributes = OAuth2ExpressionUtils.hasAnyScope(authentication, new String[] {USER_ATTRIBUTES});
        boolean addRoles = OAuth2ExpressionUtils.hasAnyScope(authentication, new String[] {ROLES});
        return getResponse(uaaPrincipal, addCustomAttributes, addRoles);
    }

    protected UaaPrincipal extractUaaPrincipal(OAuth2Authentication authentication) {
        Object object = authentication.getUserAuthentication().getPrincipal();
        if (object instanceof UaaPrincipal) {
            return (UaaPrincipal) object;
        }
        throw new IllegalStateException("User authentication could not be converted to UaaPrincipal");
    }

    protected UserInfoResponse getResponse(UaaPrincipal principal, boolean addCustomAttributes, boolean addRoles) {
        UaaUser user = userDatabase.retrieveUserById(principal.getId());
        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setGivenName(user.getGivenName());
        response.setFamilyName(user.getFamilyName());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setSub(user.getId());
        response.setPreviousLogonSuccess(user.getPreviousLogonTime());

        UserInfo info = userDatabase.getUserInfo(user.getId());
        if (addCustomAttributes && info!=null) {
            response.setAttributeValue(USER_ATTRIBUTES, info.getUserAttributes());
        }
        if (addRoles && info!=null) {
            response.setAttributeValue(ROLES, info.getRoles());
        }
        return response;
    }
}

