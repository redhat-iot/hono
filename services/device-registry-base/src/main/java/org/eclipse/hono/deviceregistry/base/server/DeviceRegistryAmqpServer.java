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

package org.eclipse.hono.deviceregistry.base.server;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.util.Constants;
import org.springframework.stereotype.Component;

@Component
public final class DeviceRegistryAmqpServer extends AmqpServiceBase<ServiceConfigProperties> {

    @Override
    protected String getServiceName() {
        return Constants.SERVICE_NAME_DEVICE_REGISTRY;
    }

}
