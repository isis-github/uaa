/*
 * *****************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */
package com.tianzhu.identity.uaa.provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.tianzhu.identity.uaa.provider.saml.idp.SamlServiceProvider;
import com.tianzhu.identity.uaa.provider.saml.idp.SamlServiceProviderConfigurator;
import com.tianzhu.identity.uaa.provider.saml.idp.SamlServiceProviderProvisioning;
import com.tianzhu.identity.uaa.provider.saml.idp.SamlSpAlreadyExistsException;
import com.tianzhu.identity.uaa.util.JsonUtils;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.web.bind.annotation.RequestMethod.*;

@RequestMapping("/saml/service-providers")
@RestController
public class SamlServiceProviderEndpoints {

    protected static Log logger = LogFactory.getLog(SamlServiceProviderEndpoints.class);

    private final SamlServiceProviderProvisioning serviceProviderProvisioning;
    private final SamlServiceProviderConfigurator samlConfigurator;

    @Autowired
    public SamlServiceProviderEndpoints(@Qualifier("serviceProviderProvisioning") SamlServiceProviderProvisioning serviceProviderProvisioning,
                                        @Qualifier("spMetaDataProviders") SamlServiceProviderConfigurator samlConfigurator) {
        this.serviceProviderProvisioning = serviceProviderProvisioning;
        this.samlConfigurator = samlConfigurator;
    }

    @RequestMapping(method = POST)
    public ResponseEntity<SamlServiceProvider> createServiceProvider(@RequestBody SamlServiceProvider body)
        throws MetadataProviderException {
        String zoneId = IdentityZoneHolder.get().getId();
        body.setIdentityZoneId(zoneId);
        samlConfigurator.validateSamlServiceProvider(body);
        SamlServiceProvider createdSp = serviceProviderProvisioning.create(body, zoneId);
        return new ResponseEntity<>(createdSp, HttpStatus.CREATED);
    }

    @RequestMapping(value = "{id}", method = PUT)
    public ResponseEntity<SamlServiceProvider> updateServiceProvider(@PathVariable String id,
                                                                     @RequestBody SamlServiceProvider body) throws MetadataProviderException {
        SamlServiceProvider existing = serviceProviderProvisioning.retrieve(id, IdentityZoneHolder.get().getId());
        String zoneId = IdentityZoneHolder.get().getId();
        body.setId(id);
        body.setIdentityZoneId(zoneId);
        if (!body.configIsValid()) {
            return new ResponseEntity<>(UNPROCESSABLE_ENTITY);
        }
        body.setEntityId(existing.getEntityId());

        samlConfigurator.validateSamlServiceProvider(body);

        SamlServiceProvider updatedSp = serviceProviderProvisioning.update(body, zoneId);
        return new ResponseEntity<>(updatedSp, OK);
    }

    @RequestMapping(method = GET)
    public ResponseEntity<List<SamlServiceProvider>> retrieveServiceProviders(
        @RequestParam(value = "active_only", required = false) String activeOnly) {
        Boolean retrieveActiveOnly = Boolean.valueOf(activeOnly);
        List<SamlServiceProvider> serviceProviderList =
            serviceProviderProvisioning.retrieveAll(retrieveActiveOnly,
                                                    IdentityZoneHolder.get().getId());
        return new ResponseEntity<>(serviceProviderList, OK);
    }

    @RequestMapping(value = "{id}", method = GET)
    public ResponseEntity<SamlServiceProvider> retrieveServiceProvider(@PathVariable String id) {
        SamlServiceProvider serviceProvider = serviceProviderProvisioning.retrieve(id, IdentityZoneHolder.get().getId());
        return new ResponseEntity<>(serviceProvider, OK);
    }

    @RequestMapping(value = "{id}", method = DELETE)
    public ResponseEntity<SamlServiceProvider> deleteServiceProvider(@PathVariable String id) {
        SamlServiceProvider serviceProvider = serviceProviderProvisioning.retrieve(id, IdentityZoneHolder.get().getId());
        serviceProviderProvisioning.delete(id, IdentityZoneHolder.get().getId());
        return new ResponseEntity<>(serviceProvider, OK);
    }

    @ExceptionHandler(MetadataProviderException.class)
    public ResponseEntity<String> handleMetadataProviderException(MetadataProviderException e) {
        if (e.getMessage().contains("Duplicate")) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        } else {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @ExceptionHandler(JsonUtils.JsonUtilException.class)
    public ResponseEntity<String> handleMetadataProviderException() {
        return new ResponseEntity<>("Invalid provider configuration.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<String> handleProviderNotFoundException() {
        return new ResponseEntity<>("Provider not found.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SamlSpAlreadyExistsException.class)
    public ResponseEntity<String> handleDuplicateServiceProvider(){
        return new ResponseEntity<>("SAML SP with the same entity id already exists.", HttpStatus.CONFLICT);
    }

}
