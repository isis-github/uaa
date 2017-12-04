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

package com.tianzhu.identity.uaa.client.event;

import com.tianzhu.identity.uaa.audit.AuditEventType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.ClientDetails;

public class SecretChangeEvent extends AbstractClientAdminEvent {

    public SecretChangeEvent(ClientDetails client, Authentication principal) {
        super(client, principal);
    }

    @Override
    public AuditEventType getAuditEventType() {
        return AuditEventType.SecretChangeSuccess;
    }

}