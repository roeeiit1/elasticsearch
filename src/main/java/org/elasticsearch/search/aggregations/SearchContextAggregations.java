/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations;

import org.elasticsearch.search.aggregations.context.AggregationContext;

import java.util.List;

/**
 * The aggregation context that is part of the search context.
 */
public class SearchContextAggregations {

    private final List<Aggregator.Factory> factories;
    private List<Aggregator> aggregators;
    private AggregationContext aggregationContext;

    /**
     * Creates a new aggregation context with all parsed aggregator factories
     *
     * @param factories The parsed aggregator factories
     */
    public SearchContextAggregations(List<Aggregator.Factory> factories) {
        this.factories = factories;
    }

    public List<Aggregator.Factory> factories() {
        return factories;
    }

    public List<Aggregator> aggregators() {
        return aggregators;
    }

    public AggregationContext valuesSourceContext() {
        return aggregationContext;
    }

    public void valuesSourceContext(AggregationContext aggregationContext) {
        this.aggregationContext = aggregationContext;
    }

    /**
     * Registers all the created aggregators (top level aggregators) for the search execution context.
     *
     * @param aggregators The top level aggregators of the search execution.
     */
    public void aggregators(List<Aggregator> aggregators) {
        this.aggregators = aggregators;
    }

}
