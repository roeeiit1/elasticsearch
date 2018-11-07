/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.client;

import org.elasticsearch.common.unit.TimeValue;

import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

/**
 * A base request for any requests that supply timeouts.
 *
 * Please note, any requests that use a ackTimeout should set timeout as they
 * represent the same backing field on the server.
 */
public abstract class TimedRequest implements Validatable {

    public static final TimeValue DEFAULT_ACK_TIMEOUT = timeValueSeconds(30);
    public static final TimeValue DEFAULT_MASTER_NODE_TIMEOUT = TimeValue.timeValueSeconds(30);

    private TimeValue timeout = DEFAULT_ACK_TIMEOUT;
    private TimeValue masterTimeout = DEFAULT_MASTER_NODE_TIMEOUT;

    public void setTimeout(TimeValue timeout) {
        this.timeout = timeout;
    }

    public void setMasterTimeout(TimeValue masterTimeout) {
        this.masterTimeout = masterTimeout;
    }

    /**
     * Returns the request timeout
     */
    public TimeValue timeout() {
        return timeout;
    }

    /**
     * Returns the timeout for the request to be completed on the master node
     */
    public TimeValue masterNodeTimeout() {
        return masterTimeout;
    }
}
