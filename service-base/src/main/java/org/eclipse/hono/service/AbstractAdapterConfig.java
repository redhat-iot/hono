/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import java.util.Optional;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.impl.CommandConnectionImpl;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.service.cache.SpringCacheProvider;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.guava.GuavaCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.google.common.cache.CacheBuilder;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Minimum Spring Boot configuration class defining beans required by protocol adapters.
 */
public abstract class AbstractAdapterConfig {

    /**
     * Exposes an OpenTracing {@code Tracer} as a Spring Bean.
     * <p>
     * The Tracer will be resolved by means of a Java service lookup.
     * If no tracer can be resolved this way, the {@code NoopTracer} is
     * returned.
     * 
     * @return The tracer.
     */
    @Bean
    public Tracer getTracer() {

        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * <p>
     * This method creates new Vert.x default options and invokes
     * {@link VertxProperties#configureVertx(VertxOptions)} on the object returned
     * by {@link #vertxProperties()}.
     * 
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        return Vertx.vertx(vertxProperties().configureVertx(new VertxOptions()));
    }

    /**
     * Exposes configuration properties for accessing a Hono Messaging service as a Spring bean.
     * <p>
     * The properties can be customized in subclasses by means of overriding the
     * {@link #customizeMessagingClientConfig(ClientConfigProperties)} method.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @ConfigurationProperties(prefix = "hono.messaging")
    @Bean
    public ClientConfigProperties messagingClientConfig() {
        final ClientConfigProperties config = new ClientConfigProperties();
        customizeMessagingClientConfig(config);
        return config;
    }

    /**
     * Further customizes the client properties provided by the {@link #messagingClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The client configuration to customize.
     */
    protected void customizeMessagingClientConfig(final ClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a client for the <em>Hono Messaging</em> component as a Spring bean.
     * <p>
     * The client is configured with the properties provided by {@link #messagingClientConfig()}.
     *
     * @return The client.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Bean
    @Scope("prototype")
    public HonoClient messagingClient() {
        return new HonoClientImpl(vertx(), messagingClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the registration service as a Spring bean.
     * <p>
     * Sets the <em>amqpHostname</em> to {@code hono-device-registry} if not set explicitly.
     *
     * @return The properties.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.registration")
    @Bean
    public RequestResponseClientConfigProperties registrationServiceClientConfig() {
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeRegistrationServiceClientConfig(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #registrationServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeRegistrationServiceClientConfig(final RequestResponseClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a client for the <em>Device Registration</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public HonoClient registrationServiceClient() {
        final HonoClientImpl result =
                new HonoClientImpl(vertx(), registrationServiceClientConfig());

        final CacheProvider cacheProvider = registrationCacheProvider();
        if (cacheProvider != null) {
            result.setCacheProvider(cacheProvider);
        }

        return result;
    }

    /**
     * Exposes the provider for caches as a Spring bean.
     *
     * @return The provider instance.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public CacheProvider registrationCacheProvider() {
        return newGuavaCache(registrationServiceClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the credentials service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.credentials")
    @Bean
    public ClientConfigProperties credentialsServiceClientConfig() {
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeCredentialsServiceClientConfig(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #credentialsServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeCredentialsServiceClientConfig(final RequestResponseClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a client for the <em>Credentials</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public HonoClient credentialsServiceClient() {
        return new HonoClientImpl(vertx(), credentialsServiceClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the tenant service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.tenant")
    @Bean
    public RequestResponseClientConfigProperties tenantServiceClientConfig() {
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeTenantServiceClientConfig(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #tenantServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeTenantServiceClientConfig(final RequestResponseClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a client for the <em>Tenant</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public HonoClient tenantServiceClient() {

        final HonoClientImpl result = new HonoClientImpl(vertx(), tenantServiceClientConfig());

        final CacheProvider cacheProvider = tenantCacheProvider();
        if (cacheProvider != null) {
            result.setCacheProvider(cacheProvider);
        }

        return result;
    }

    /**
     * Exposes the provider for caches as a Spring bean.
     * 
     * @return The provider instance.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public CacheProvider tenantCacheProvider() {
        return newGuavaCache(tenantServiceClientConfig());
    }

    /**
     * Exposes configuration properties for Command and Control.
     *
     * @return The Properties.
     */
    @Qualifier(CommandConstants.COMMAND_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.command")
    @Bean
    public ClientConfigProperties commandConnectionClientConfig() {
        return new ClientConfigProperties();
    }

    /**
     * Exposes the Command and Control connection.
     *
     * @return The Connection.
     */
    @Bean
    @Scope("prototype")
    public CommandConnection commandConnection() {
        return new CommandConnectionImpl(vertx(), commandConnectionClientConfig());
    }

    /**
     * Exposes configuration options for vertx.
     * 
     * @return The Properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Create a new cache provider based on Guava and Spring Cache.
     * 
     * @param config The configuration to use as base for this cache.
     * @return A new cache provider or {@code null} if no cache should be used.
     */
    private static CacheProvider newGuavaCache(final RequestResponseClientConfigProperties config) {
        final int minCacheSize = config.getResponseCacheMinSize();
        final long maxCacheSize = config.getResponseCacheMaxSize();

        if (maxCacheSize <= 0) {
            return null;
        }

        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .initialCapacity(minCacheSize)
                .maximumSize(Math.max(minCacheSize, maxCacheSize));

        final GuavaCacheManager manager = new GuavaCacheManager();
        manager.setAllowNullValues(false);
        manager.setCacheBuilder(builder);

        return new SpringCacheProvider(manager);
    }

    /**
     * Exposes properties for configuring the application properties as a Spring bean.
     *
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties() {
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), applicationConfigProperties());
    }
}
