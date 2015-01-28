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

package org.elasticsearch.search.aggregations.transformer.moving.avg;

import com.google.common.collect.EvictingQueue;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalHistogram;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;
import org.elasticsearch.search.aggregations.transformer.DiscontinuousHistogram;
import org.elasticsearch.search.aggregations.transformer.DiscontinuousHistogram.GapPolicy;
import org.elasticsearch.search.aggregations.transformer.InternalSimpleValue;
import org.elasticsearch.search.aggregations.transformer.models.MovingAvgModel;
import org.elasticsearch.search.aggregations.transformer.models.MovingAvgModel.Weighting;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class InternalMovingAvg<B extends InternalHistogram.Bucket> extends InternalHistogram<B> implements MovingAvg {

    final static Type TYPE = new Type("moving_average", "movavg");

    private final static AggregationStreams.Stream STREAM = new AggregationStreams.Stream() {
        @Override
        public InternalMovingAvg readResult(StreamInput in) throws IOException {
            InternalMovingAvg histogram = new InternalMovingAvg();
            histogram.readFrom(in);
            return histogram;
        }
    };

    public static void registerStream() {
        AggregationStreams.registerStream(STREAM, TYPE.stream());
    }

    private GapPolicy gapPolicy;
    private InternalAggregations aggregations;
    private int windowSize;
    private Weighting weight;


    InternalMovingAvg() {
        super();
    }

    public InternalMovingAvg(String name, boolean keyed, @Nullable ValueFormatter formatter, GapPolicy gapPolicy,
                             int windowSize, Weighting weight, InternalAggregations subAggregations, Map<String, Object> metaData) {
        super(name, Collections.EMPTY_LIST, null, 1, null, formatter, keyed, metaData);
        this.gapPolicy = gapPolicy;
        this.aggregations = subAggregations;
        this.windowSize = windowSize;
        this.weight = weight;
    }

    public InternalMovingAvg(String name, List<B> buckets, @Nullable ValueFormatter formatter, boolean keyed, GapPolicy gapPolicy,
                             int windowSize, Weighting weight, Map<String, Object> metaData) {
        super(name, buckets, null, 1, null, formatter, keyed, metaData);
        this.gapPolicy = gapPolicy;
        this.windowSize = windowSize;
        this.weight = weight;
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public InternalAggregation reduce(ReduceContext reduceContext) {
        List<InternalAggregation> aggregations = reduceContext.aggregations();
        List<InternalAggregations> subAggregationsList = new ArrayList<>(aggregations.size());
        for (InternalAggregation aggregation : aggregations) {
            assert aggregation.getName().equals(getName());
            subAggregationsList.add(((InternalMovingAvg<B>) aggregation).aggregations);
        }
        final InternalAggregations aggs = InternalAggregations.reduce(subAggregationsList, reduceContext);
        InternalHistogram histo = (InternalHistogram) aggs.iterator().next();
        Factory factory = histo.getFactory();
        DiscontinuousHistogram histoBuckets = new DiscontinuousHistogram(histo.getBuckets(), gapPolicy);
        List<InternalHistogram.Bucket> newBuckets = new ArrayList<>();


        Map<String, EvictingQueue<Double>> bucketWindows = new HashMap<>();
        EvictingQueue<Long> docCountWindow = EvictingQueue.create(this.windowSize);

        for (DiscontinuousHistogram.BucketMetrics metrics : histoBuckets) {
            docCountWindow.offer(metrics.owningBucket.getDocCount());

            for (Map.Entry<String, Double> entry : metrics.singleValues.entrySet()) {
                EvictingQueue<Double> v = bucketWindows.get(entry.getKey());
                if (v == null) {
                    v = EvictingQueue.create(this.windowSize);
                }
                v.offer(entry.getValue());
                bucketWindows.put(entry.getKey(), v);
            }

            // NOCOMMIT TODO multi-metrics here

            long newBucketKey = metrics.owningBucket.getKeyAsNumber().longValue();
            InternalHistogram.Bucket newBucket = generateBucket(newBucketKey, docCountWindow, bucketWindows, factory);
            newBuckets.add(newBucket);
        }

        return new InternalMovingAvg<>(getName(), newBuckets, formatter(), keyed(), gapPolicy, windowSize, weight, metaData);
    }

    private InternalHistogram.Bucket generateBucket(long newBucketKey, EvictingQueue<Long> docCountWindow,
                                                    Map<String, EvictingQueue<Double>> bucketWindows,
                                                    Factory factory) {

        List<InternalAggregation> metricsAggregations = new ArrayList<>();
        double docCountMovAvg = MovingAvgModel.next(docCountWindow, weight);

        InternalSimpleValue docCountMovAvgAgg = new InternalSimpleValue("_doc_count", docCountMovAvg, null); // NOCOMMIT change the name of this to something less confusing
        metricsAggregations.add(docCountMovAvgAgg);

        for (Entry<String, EvictingQueue<Double>> entry : bucketWindows.entrySet()) {
            double movAvg = MovingAvgModel.next(entry.getValue(), weight);
            InternalSimpleValue metricAgg = new InternalSimpleValue(entry.getKey(), movAvg, null);
            metricsAggregations.add(metricAgg);
        }
        InternalAggregations metricsAggs = new InternalAggregations(metricsAggregations);
        return factory.createBucket(newBucketKey, 0, metricsAggs, keyed(), formatter());
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        super.doWriteTo(out);
        if (aggregations == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            aggregations.writeTo(out);
        }
        gapPolicy.writeTo(out);
        weight.writeTo(out);
        out.writeVInt(windowSize);
    }

    @Override
    protected void doReadFrom(StreamInput in) throws IOException {
        super.doReadFrom(in);
        if (in.readBoolean()) {
            aggregations = InternalAggregations.readAggregations(in);
        } else {
            aggregations = null;
        }
        gapPolicy = GapPolicy.readFrom(in);
        weight = Weighting.readFrom(in);
        windowSize = in.readVInt();
    }

}
