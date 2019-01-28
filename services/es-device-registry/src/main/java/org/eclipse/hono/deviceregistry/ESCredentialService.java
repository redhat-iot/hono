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
package org.eclipse.hono.deviceregistry;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpHost;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.credentials.CompleteBaseCredentialsService;
import org.eclipse.hono.service.tenant.CompleteBaseTenantService;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentiles;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Iterator;

/**
 *
 */
public class ESCredentialService extends CompleteBaseCredentialsService<ESCredentialConfigProperties> {

    RestHighLevelClient client;
    private final String EC_CREDENTIALS_INDEX = "credentials";

    /**
     * Creates a new service instance for a password encoder.
     *
     * @param pwdEncoder The encoder to use for hashing clear text passwords.
     * @throws NullPointerException if encoder is {@code null}.
     */
    protected ESCredentialService(HonoPasswordEncoder pwdEncoder) {
        super(pwdEncoder);
    }

    @Override
    public void setConfig(final ESCredentialConfigProperties configuration) {
        client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9299, "http")));
    }

    @Override
    public void add(String tenantId, JsonObject otherKeys, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        String credentialUID = tenantId + otherKeys.getValue(CredentialsConstants.FIELD_TYPE) + otherKeys.getValue(CredentialsConstants.FIELD_AUTH_ID);

        // then add the credential.
        otherKeys.put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        final IndexRequest request = new IndexRequest(EC_CREDENTIALS_INDEX, "doc", credentialUID)
                .source(otherKeys, XContentType.JSON)
                .opType(DocWriteRequest.OpType.CREATE);
        client.indexAsync(request, RequestOptions.DEFAULT, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(final IndexResponse indexResponse) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(indexResponse.status().getStatus())));
            }

            @Override
            public void onFailure(final Exception e) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CONFLICT)));
            }
        });
    }


    @Override
    public void update(String tenantId, JsonObject otherKeys, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        String credentialUID = tenantId + otherKeys.getValue(CredentialsConstants.FIELD_TYPE) + otherKeys.getValue(CredentialsConstants.FIELD_AUTH_ID);

        otherKeys.put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        final UpdateRequest request = new UpdateRequest(EC_CREDENTIALS_INDEX, "doc", credentialUID)
                .doc(otherKeys, XContentType.JSON);
        client.updateAsync(request, RequestOptions.DEFAULT, new ActionListener<UpdateResponse>() {
            @Override
            public void onResponse(final UpdateResponse updateResponse) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(updateResponse.status().getStatus())));
            }

            @Override
            public void onFailure(final Exception e) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CONFLICT)));
            }
        });
    }

    @Override
    public void remove(String tenantId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        String credentialUID = tenantId + type + authId;

        final DeleteRequest request = new DeleteRequest(EC_CREDENTIALS_INDEX, "doc", credentialUID);

        client.deleteAsync(request, RequestOptions.DEFAULT, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(final DeleteResponse deleteResponse) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(deleteResponse.status().getStatus())));
            }

            @Override
            public void onFailure(final Exception e) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        });
    }
    }

    @Override
    public void get(String tenantId, String type, String authId, Span span, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        String credentialUID = tenantId + type + authId;

        final GetRequest request = new GetRequest(EC_CREDENTIALS_INDEX, "doc", credentialUID);
        client.getAsync(request, RequestOptions.DEFAULT, new ActionListener<GetResponse>() {
            @Override
            public void onResponse(GetResponse getResponse) {
                resultHandler.handle(Future.succeededFuture(
                        CredentialsResult.from(HttpURLConnection.HTTP_OK,
                                JsonObject.mapFrom(getResponse.getSourceAsString()))));
            }

            @Override
            public void onFailure(final Exception e) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        });
    }

    @Override
    public void get(String tenantId, String type, String authId, JsonObject clientContext, Span span, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        super.get(tenantId, type, authId, clientContext, span, resultHandler);
    }


    @Override
    public void getAll(String tenantId, String deviceId, Span span, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        SearchRequest request = new SearchRequest(EC_CREDENTIALS_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId))
                        .must(QueryBuilders.matchQuery(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId));
        request.source(searchSourceBuilder);

        client.searchAsync(request, RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                JsonArray response = new JsonArray();
                Iterator<SearchHit> iterator = searchResponse.getHits().iterator();
                while( iterator.hasNext()) {
                    response.add(new JsonObject(((SearchHit) iterator.next()).getSourceAsString()));
                }
                resultHandler.handle(Future.succeededFuture(
                        CredentialsResult.from(HttpURLConnection.HTTP_OK,
                                JsonObject.mapFrom(response))));
            }

            @Override
            public void onFailure(final Exception e) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        });
    }


    @Override
    public void stop() throws Exception {
        client.close();
    }


    private SearchHit searchCredential(String tenantId, String authId, String type){
        SearchRequest request = new SearchRequest(EC_CREDENTIALS_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId))
                        .must(QueryBuilders.matchQuery(CredentialsConstants.FIELD_AUTH_ID, authId))
                        .must(QueryBuilders.matchQuery(CredentialsConstants.FIELD_TYPE, type));
        request.source(searchSourceBuilder);

        try {
            SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

            if (searchResponse.getHits().totalHits == 1) {
                return searchResponse.getHits().getAt(0);
            } else return null;
        }catch (IOException e) {
            return null;
        }
    }
}
