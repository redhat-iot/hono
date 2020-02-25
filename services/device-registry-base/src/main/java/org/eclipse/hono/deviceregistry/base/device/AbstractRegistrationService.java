/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.base.device;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.eclipse.hono.deviceregistry.base.device.DeviceKey.deviceKey;
import static org.eclipse.hono.service.MoreFutures.completeHandler;

import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.deviceregistry.base.tenant.TenantInformationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public abstract class AbstractRegistrationService extends org.eclipse.hono.service.registration.AbstractRegistrationService {

    @Autowired
    protected TenantInformationService tenantInformationService;

    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    @Override
    protected void getDevice(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        completeHandler(() -> processGetDevice(tenantId, deviceId, span), resultHandler);
    }

    protected CompletableFuture<RegistrationResult> processGetDevice(final String tenantName, final String deviceId, final Span span) {

        return this.tenantInformationService
                .tenantExists(tenantName, HTTP_NOT_FOUND, span)
                .thenCompose(tenantId -> processGetDevice(deviceKey(tenantId, deviceId), span));

    }

    protected abstract CompletableFuture<RegistrationResult> processGetDevice(DeviceKey key, Span span);

}
