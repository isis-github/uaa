package com.tianzhu.identity.uaa.zone;

import org.apache.commons.lang.StringUtils;
import com.tianzhu.identity.uaa.client.ClientDetailsValidator;
import com.tianzhu.identity.uaa.client.InvalidClientDetailsException;
import com.tianzhu.identity.uaa.constants.OriginKeys;
import com.tianzhu.identity.uaa.oauth.client.ClientConstants;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;

import java.util.Collections;

import static com.tianzhu.identity.uaa.client.ClientAdminEndpointsValidator.checkRequestedGrantTypes;
import static com.tianzhu.identity.uaa.oauth.token.TokenConstants.*;

public class ZoneEndpointsClientDetailsValidator implements ClientDetailsValidator {

    private final String requiredScope;

    private ClientSecretValidator clientSecretValidator;

    public ZoneEndpointsClientDetailsValidator(String requiredScope) {
        this.requiredScope = requiredScope;
    }


    @Override
    public ClientDetails validate(ClientDetails clientDetails, Mode mode) throws InvalidClientDetailsException {

        if (mode == Mode.CREATE) {
            if (!Collections.singleton("openid").equals(clientDetails.getScope())) {
                throw new InvalidClientDetailsException("only openid scope is allowed");
            }
            if (!Collections.singleton("uaa.resource").equals(AuthorityUtils.authorityListToSet(clientDetails.getAuthorities()))) {
                throw new InvalidClientDetailsException("only uaa.resource authority is allowed");
            }
            if (StringUtils.isBlank(clientDetails.getClientId())) {
                throw new InvalidClientDetailsException("client_id cannot be blank");
            }
            checkRequestedGrantTypes(clientDetails.getAuthorizedGrantTypes());
            if (clientDetails.getAuthorizedGrantTypes().contains("client_credentials") ||
                clientDetails.getAuthorizedGrantTypes().contains("authorization_code") ||
                clientDetails.getAuthorizedGrantTypes().contains(GRANT_TYPE_USER_TOKEN) ||
                clientDetails.getAuthorizedGrantTypes().contains(GRANT_TYPE_REFRESH_TOKEN) ||
                clientDetails.getAuthorizedGrantTypes().contains(GRANT_TYPE_SAML2_BEARER) ||
                clientDetails.getAuthorizedGrantTypes().contains(GRANT_TYPE_JWT_BEARER) ||
                clientDetails.getAuthorizedGrantTypes().contains("password")) {
                if (StringUtils.isBlank(clientDetails.getClientSecret())) {
                    throw new InvalidClientDetailsException("client_secret cannot be blank");
                }
                clientSecretValidator.validate(clientDetails.getClientSecret());
            }
            if (!Collections.singletonList(OriginKeys.UAA).equals(clientDetails.getAdditionalInformation().get(ClientConstants.ALLOWED_PROVIDERS))) {
                throw new InvalidClientDetailsException("only the internal IdP ('uaa') is allowed");
            }


            BaseClientDetails validatedClientDetails = new BaseClientDetails(clientDetails);
            validatedClientDetails.setAdditionalInformation(clientDetails.getAdditionalInformation());
            validatedClientDetails.setResourceIds(Collections.singleton("none"));
            validatedClientDetails.addAdditionalInformation(ClientConstants.CREATED_WITH, requiredScope);
            return validatedClientDetails;
        } else if (mode == Mode.MODIFY) {
            throw new IllegalStateException("This validator cannot be used for modification requests");
        } else if (mode == Mode.DELETE) {
            if (!requiredScope.equals(clientDetails.getAdditionalInformation().get(ClientConstants.CREATED_WITH))) {
                throw new InvalidClientDetailsException("client must have been "+ClientConstants.CREATED_WITH+" scope "+requiredScope);
            }
            return clientDetails;
        }
        throw new IllegalStateException("This validator must be called with a mode");
    }


    @Override
    public ClientSecretValidator getClientSecretValidator() {
        return this.clientSecretValidator;
    }

    public void setClientSecretValidator(ClientSecretValidator clientSecretValidator) {
        this.clientSecretValidator = clientSecretValidator;
    }
}
