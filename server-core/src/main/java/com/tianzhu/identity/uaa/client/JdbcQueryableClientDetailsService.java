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
package com.tianzhu.identity.uaa.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.tianzhu.identity.uaa.audit.event.EntityDeletedEvent;
import com.tianzhu.identity.uaa.resources.QueryableResourceManager;
import com.tianzhu.identity.uaa.resources.jdbc.AbstractQueryable;
import com.tianzhu.identity.uaa.resources.jdbc.JdbcPagingListFactory;
import com.tianzhu.identity.uaa.util.JsonUtils;
import com.tianzhu.identity.uaa.zone.MultitenantJdbcClientDetailsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class JdbcQueryableClientDetailsService extends AbstractQueryable<ClientDetails> implements
                QueryableResourceManager<ClientDetails> {

    private static final Log logger = LogFactory.getLog(JdbcQueryableClientDetailsService.class);

    private MultitenantJdbcClientDetailsService delegate;

    private static final String CLIENT_FIELDS = "client_id, client_secret, resource_ids, scope, "
                    + "authorized_grant_types, web_server_redirect_uri, authorities, access_token_validity, "
                    + "refresh_token_validity, additional_information, autoapprove, lastmodified";

    public static final String CLIENT_DETAILS_TABLE = "oauth_client_details";
    private static final String BASE_FIND_STATEMENT = "select " + CLIENT_FIELDS
        + " from " + CLIENT_DETAILS_TABLE;

    public JdbcQueryableClientDetailsService(MultitenantJdbcClientDetailsService delegate, JdbcTemplate jdbcTemplate,
                    JdbcPagingListFactory pagingListFactory) {
        super(jdbcTemplate, pagingListFactory, new ClientDetailsRowMapper());
        this.delegate = delegate;
    }

    @Override
    protected String getBaseSqlQuery() {
        return BASE_FIND_STATEMENT;
    }
    @Override
    protected String getTableName() {
        return CLIENT_DETAILS_TABLE;
    }

    @Override
    public List<ClientDetails> retrieveAll(String zoneId) {
        return delegate.listClientDetails(zoneId);
    }

    @Override
    public ClientDetails retrieve(String id, String zoneId) {
        return delegate.loadClientByClientId(id, zoneId);
    }

    @Override
    public ClientDetails create(ClientDetails resource, String zoneId) {
        delegate.addClientDetails(resource, zoneId);
        return delegate.loadClientByClientId(resource.getClientId(), zoneId);
    }

    @Override
    public ClientDetails update(String id, ClientDetails resource, String zoneId) {
        delegate.updateClientDetails(resource, zoneId);
        return delegate.loadClientByClientId(id, zoneId);
    }

    @Override
    public ClientDetails delete(String id, int version, String zoneId) {
        ClientDetails client = delegate.loadClientByClientId(id, zoneId);
        delegate.onApplicationEvent(new EntityDeletedEvent<>(client, SecurityContextHolder.getContext().getAuthentication()));
        return client;
    }

    @Override
    protected void validateOrderBy(String orderBy) throws IllegalArgumentException {
        super.validateOrderBy(orderBy, CLIENT_FIELDS);
    }

    private static class ClientDetailsRowMapper implements RowMapper<ClientDetails> {

        @Override
        public ClientDetails mapRow(ResultSet rs, int rowNum) throws SQLException {
            BaseClientDetails details = new BaseClientDetails(rs.getString(1), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(7), rs.getString(6));
            details.setClientSecret(rs.getString(2));
            if (rs.getObject(8) != null) {
                details.setAccessTokenValiditySeconds(rs.getInt(8));
            }
            if (rs.getObject(9) != null) {
                details.setRefreshTokenValiditySeconds(rs.getInt(9));
            }
            String json = rs.getString(10);
            if (json != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> additionalInformation = JsonUtils.readValue(json, Map.class);
                    details.setAdditionalInformation(additionalInformation);
                } catch (Exception e) {
                    logger.warn("Could not decode JSON for additional information: " + details, e);
                }
            }
            String scopes = rs.getString(11);
            if (scopes != null) {
                details.setAutoApproveScopes(StringUtils.commaDelimitedListToSet(scopes));
            }
            if (rs.getTimestamp(12) != null) {
                details.addAdditionalInformation("lastModified", rs.getTimestamp(12));
            }
            return details;
        }
    }
}
