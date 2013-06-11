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

package org.elasticsearch.search.aggregations.bucket.multi.histogram.date;

import com.google.common.collect.Lists;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.bucket.single.SingleBucketAggregator;

import java.util.List;

/**
 *
 */
public class UnmappedDateHistogramAggregator extends Aggregator {

    private final InternalDateOrder order;
    private final boolean keyed;

    public UnmappedDateHistogramAggregator(String name, InternalDateOrder order, boolean keyed, Aggregator parent) {
        super(name, parent);
        this.order = order;
        this.keyed = keyed;
    }

    @Override
    public Collector collector() {
        return null;
    }

    @Override
    public InternalAggregation buildAggregation() {
        List<DateHistogram.Bucket> buckets = Lists.newArrayListWithCapacity(0);
        return new InternalDateHistogram(name, buckets, order, keyed);
    }

    public static class Factory extends SingleBucketAggregator.CompoundFactory<UnmappedDateHistogramAggregator> {

        private final InternalDateOrder order;
        private final boolean keyed;

        public Factory(String name, InternalDateOrder order, boolean keyed) {
            super(name);
            this.order = order;
            this.keyed = keyed;
        }

        @Override
        public UnmappedDateHistogramAggregator create(Aggregator parent) {
            return new UnmappedDateHistogramAggregator(name, order, keyed, parent);
        }
    }

}
