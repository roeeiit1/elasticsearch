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
import org.elasticsearch.search.aggregations.context.ValuesSource;
import org.elasticsearch.search.aggregations.context.ValuesSourceBased;

/**
 * An aggregator that aggregates based on values that are provided by a {@link ValuesSource}.
 */
public abstract class ValuesSourceAggregator<VS extends ValuesSource> extends Aggregator implements ValuesSourceBased {

    protected final VS valuesSource;

    public ValuesSourceAggregator(String name,
                                  VS valuesSource,
                                  Class<VS> requiredValuesSourceType,
                                  AggregationContext aggregationContext,
                                  Aggregator parent) {

        super(name, aggregationContext, parent);
        if (valuesSource != null) {
            this.valuesSource = valuesSource;
        } else {
            this.valuesSource = resolveValuesSourceFromAncestors(name, parent, requiredValuesSourceType);
        }
    }

    @Override
    public ValuesSource valuesSource() {
        return valuesSource;
    }

    public static <VS extends ValuesSource> VS resolveValuesSourceFromAncestors(String aggName, Aggregator parent, Class<VS> requiredValuesSourceType) {
        ValuesSource vs;
        while (parent != null) {
            if (parent instanceof ValuesSourceBased) {
                vs = ((ValuesSourceBased) parent).valuesSource();
                if (requiredValuesSourceType == null || requiredValuesSourceType.isInstance(vs)) {
                    return (VS) vs;
                }
            }
            parent = parent.parent();
        }
        throw new AggregationExecutionException("could not find the appropriate value context to perform aggregation [" + aggName + "]");
    }

}
