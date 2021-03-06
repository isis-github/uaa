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
import com.tianzhu.identity.uaa.web.ConvertingExceptionView;
import com.tianzhu.identity.uaa.web.ExceptionReport;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class ClientMetadataAdminEndpoints {

    @Autowired
    @Qualifier("jdbcClientMetadataProvisioning")
    private ClientMetadataProvisioning clientMetadataProvisioning;

    private HttpMessageConverter<?>[] messageConverters = new RestTemplate().getMessageConverters().toArray(
            new HttpMessageConverter<?>[0]);;

    private static Log logger = LogFactory.getLog(ClientMetadataAdminEndpoints.class);

    @RequestMapping(value = "/oauth/clients/{client}/meta", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public ClientMetadata retrieveClientMetadata(@PathVariable("client") String clientId) {
        try {
            return clientMetadataProvisioning.retrieve(clientId, IdentityZoneHolder.get().getId());
        } catch (EmptyResultDataAccessException erdae) {
            throw new ClientMetadataException("No client metadata found for " + clientId, HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(value = "/oauth/clients/meta", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<ClientMetadata> retrieveAllClientMetadata() {
        return clientMetadataProvisioning.retrieveAll(IdentityZoneHolder.get().getId());
    }

    @RequestMapping(value = "/oauth/clients/{client}/meta", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public ClientMetadata updateClientMetadata(@RequestBody ClientMetadata clientMetadata,
                                               @PathVariable("client") String clientId) {

        if (StringUtils.hasText(clientMetadata.getClientId())) {
            if (!clientId.equals(clientMetadata.getClientId())) {
                throw new ClientMetadataException("Client ID in body {" + clientMetadata.getClientId() + "} does not match URL path {" + clientId + "}", HttpStatus.BAD_REQUEST);
            }
        } else {
            clientMetadata.setClientId(clientId);
        }
        try {
            return clientMetadataProvisioning.update(clientMetadata, IdentityZoneHolder.get().getId());
        } catch (EmptyResultDataAccessException e) {
            throw new ClientMetadataException("No client with ID " + clientMetadata.getClientId(), HttpStatus.NOT_FOUND);
        }
    }

    @ExceptionHandler
    public View handleException(ClientMetadataException cme, HttpServletRequest request) {
        logger.error("Unhandled exception in client metadata admin endpoints.", cme);

        boolean trace = request.getParameter("trace") != null && !request.getParameter("trace").equals("false");
        return new ConvertingExceptionView(new ResponseEntity<>(new ExceptionReport(cme, trace, cme.getExtraInfo()),
            cme.getStatus()), messageConverters);
    }

    public void setClientMetadataProvisioning(ClientMetadataProvisioning clientMetadataProvisioning) {
        this.clientMetadataProvisioning = clientMetadataProvisioning;
    }

    public void setMessageConverters(HttpMessageConverter<?>[] messageConverters) {
        this.messageConverters = messageConverters;
    }
}
