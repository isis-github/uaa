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

package com.tianzhu.identity.uaa.account.event;

import com.tianzhu.identity.uaa.audit.AuditEvent;
import com.tianzhu.identity.uaa.audit.AuditEventType;
import com.tianzhu.identity.uaa.user.UaaUser;
import org.springframework.security.core.Authentication;

/**
 * @author Dave Syer
 * 
 */
public class PasswordChangeEvent extends AbstractPasswordChangeEvent {

    public PasswordChangeEvent(String message, UaaUser user, Authentication principal) {
        super(message, user, principal);
    }

    @Override
    public AuditEvent getAuditEvent() {
        return createAuditRecord(getUser().getId(), AuditEventType.PasswordChangeSuccess,
                        getOrigin(getPrincipal()), getMessage());
    }

}
