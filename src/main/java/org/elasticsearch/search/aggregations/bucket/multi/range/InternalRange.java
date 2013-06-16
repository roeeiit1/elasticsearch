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

package org.elasticsearch.search.aggregations.bucket.multi.range;

import com.google.common.collect.Lists;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregationStreams;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.context.numeric.ValueFormatter;
import org.elasticsearch.search.aggregations.context.numeric.ValueFormatterStreams;

import java.io.IOException;
import java.util.*;

/**
 *
 */
public class InternalRange<B extends InternalRange.Bucket> extends InternalAggregation implements Range<B> {

    static final Factory FACTORY = new Factory();

    public final static Type TYPE = new Type("range");

    private final static AggregationStreams.Stream STREAM = new AggregationStreams.Stream<InternalRange>() {
        @Override
        public InternalRange readResult(StreamInput in) throws IOException {
            InternalRange ranges = new InternalRange();
            ranges.readFrom(in);
            return ranges;
        }
    };

    public static void registerStream() {
        AggregationStreams.registerStream(STREAM, TYPE.stream());
    }

    public static class Bucket implements Range.Bucket {

        private double from = Double.NEGATIVE_INFINITY;
        private double to = Double.POSITIVE_INFINITY;
        private long docCount;
        private InternalAggregations aggregations;
        private String key;

        public Bucket(String key, double from, double to, long docCount, InternalAggregations aggregations) {
            this.key = key;
            this.from = from;
            this.to = to;
            this.docCount = docCount;
            this.aggregations = aggregations;
        }

        public String getKey() {
            return key;
        }

        @Override
        public double getFrom() {
            return from;
        }

        @Override
        public double getTo() {
            return to;
        }

        @Override
        public long getDocCount() {
            return docCount;
        }

        @Override
        public Aggregations getAggregations() {
            return aggregations;
        }

        Bucket reduce(List<Bucket> ranges) {
            if (ranges.size() == 1) {
                return ranges.get(0);
            }
            Bucket reduced = null;
            List<InternalAggregations> aggregationsList = Lists.newArrayListWithCapacity(ranges.size());
            for (Bucket range : ranges) {
                if (reduced == null) {
                    reduced = range;
                } else {
                    reduced.docCount += range.docCount;
                }
                aggregationsList.add(range.aggregations);
            }
            reduced.aggregations = InternalAggregations.reduce(aggregationsList);
            return reduced;
        }

        void toXContent(XContentBuilder builder, Params params, ValueFormatter formatter, boolean keyed) throws IOException {
            if (keyed) {
                builder.startObject(key(this, formatter));
            } else {
                builder.startObject();
                if (key != null) {
                    builder.field(CommonFields.KEY, key);
                }
            }
            if (!Double.isInfinite(from)) {
                builder.field(CommonFields.FROM, from);
                if (formatter != null) {
                    builder.field(CommonFields.FROM_AS_STRING, formatter.format(from));
                }
            }
            if (!Double.isInfinite(to)) {
                builder.field(CommonFields.TO, to);
                if (formatter != null) {
                    builder.field(CommonFields.TO_AS_STRING, formatter.format(to));
                }
            }
            builder.field(CommonFields.DOC_COUNT, docCount);
            aggregations.toXContentInternal(builder, params);
            builder.endObject();
        }

    }

    public static class Factory<B extends InternalRange.Bucket> {

        public InternalRange<B> create(String name, List<B> buckets, ValueFormatter formatter, boolean keyed) {
            return new InternalRange<B>(name, buckets, formatter, keyed);
        }

        public B createBucket(String key, double from, double to, long docCount, InternalAggregations aggregations) {
            return (B) new Bucket(key, from, to, docCount, aggregations);
        }

    }

    private List<B> ranges;
    private Map<String, B> rangeMap;
    private ValueFormatter formatter;
    private boolean keyed;

    public InternalRange() {} // for serialization

    public InternalRange(String name, List<B> ranges, ValueFormatter formatter, boolean keyed) {
        super(name);
        this.ranges = ranges;
        this.formatter = formatter;
        this.keyed = keyed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<B> iterator() {
        Object iter = ranges.iterator();
        return (Iterator<B>) iter;
    }

    @Override
    public B getByKey(String key) {
        if (rangeMap == null) {
            rangeMap = new HashMap<String, B>();
            for (Range.Bucket bucket : ranges) {
                rangeMap.put(key(bucket, formatter), (B) bucket);
            }
        }
        return (B) rangeMap.get(key);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public InternalRange reduce(List<InternalAggregation> aggregations) {
        if (aggregations.size() == 1) {
            return (InternalRange) aggregations.get(0);
        }
        List<List<B>> rangesList = null;
        for (InternalAggregation aggregation : aggregations) {
            InternalRange<B> ranges = (InternalRange<B>) aggregation;
            if (rangesList == null) {
                rangesList = new ArrayList<List<B>>(ranges.ranges.size());
                for (B bucket : ranges.ranges) {
                    List<B> sameRangeList = new ArrayList<B>(aggregations.size());
                    sameRangeList.add(bucket);
                    rangesList.add(sameRangeList);
                }
            } else {
                int i = 0;
                for (B range : ranges.ranges) {
                    rangesList.get(i++).add(range);
                }
            }
        }

        InternalRange reduced = (InternalRange) aggregations.get(0);
        int i = 0;
        for (List<B> sameRangeList : rangesList) {
            reduced.ranges.set(i++, (sameRangeList.get(0)).reduce((List<Bucket>) sameRangeList));
        }
        return reduced;
    }

    protected B createBucket(String key, double from, double to, long docCount, InternalAggregations aggregations) {
        return (B) new Bucket(key, from, to, docCount, aggregations);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        name = in.readString();
        formatter = ValueFormatterStreams.readOptional(in);
        keyed = in.readBoolean();
        int size = in.readVInt();
        List<B> ranges = Lists.newArrayListWithCapacity(size);
        for (int i = 0; i < size; i++) {
            String key = in.readOptionalString();
            ranges.add(createBucket(key, in.readDouble(), in.readDouble(), in.readVLong(), InternalAggregations.readAggregations(in)));
        }
        this.ranges = ranges;
        this.rangeMap = null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        ValueFormatterStreams.writeOptional(formatter, out);
        out.writeBoolean(keyed);
        out.writeVInt(ranges.size());
        for (B bucket : ranges) {
            out.writeOptionalString(((Bucket) bucket).key);
            out.writeDouble(((Bucket) bucket).from);
            out.writeDouble(((Bucket) bucket).to);
            out.writeVLong(((Bucket) bucket).docCount);
            ((Bucket) bucket).aggregations.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (keyed) {
            builder.startObject(name);
        } else {
            builder.startArray(name);
        }
        for (B range : ranges) {
            range.toXContent(builder, params, formatter, keyed);
        }
        if (keyed) {
            builder.endObject();
        } else {
            builder.endArray();
        }
        return builder;
    }

    private static String key(Range.Bucket bucket, ValueFormatter formatter) {
        String key = bucket.getKey();
        if (key != null) {
            return key;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Double.isInfinite(bucket.getFrom()) ? "*" : formatter != null ? formatter.format(bucket.getFrom()) : bucket.getFrom());
        sb.append("-");
        sb.append(Double.isInfinite(bucket.getTo()) ? "*" : formatter != null ? formatter.format(bucket.getTo()) : bucket.getTo());
        return sb.toString();
    }
}
