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

package org.elasticsearch.search.aggregations.calc.numeric;

import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.context.AggregationContext;

/**
 *
 */
public class UnmappedNumericAggregator<S extends NumericAggregation> extends Aggregator {

    private final NumericAggregation.Factory<S> statsFactory;

    public UnmappedNumericAggregator(String name, NumericAggregation.Factory<S> statsFactory,
                                     AggregationContext aggregationContext, Aggregator parent) {
        super(name, aggregationContext, parent);
        this.statsFactory = statsFactory;
    }

    @Override
    public Collector collector() {
        return null;
    }

    @Override
    public S buildAggregation() {
        return statsFactory.createUnmapped(name);
    }

    public static class Factory<S extends NumericAggregation> extends Aggregator.Factory<UnmappedNumericAggregator<S>> {

        private final NumericAggregation.Factory<S> statsFactory;

        public Factory(String name, NumericAggregation.Factory<S> statsFactory) {
            super(name);
            this.statsFactory = statsFactory;
        }

        @Override
        public UnmappedNumericAggregator<S> create(AggregationContext aggregationContext, Aggregator parent) {
            return new UnmappedNumericAggregator<S>(name, statsFactory, aggregationContext, parent);
        }
    }

}
