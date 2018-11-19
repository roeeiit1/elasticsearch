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
package org.elasticsearch.search.aggregations.bucket.terms;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregator.SubAggCollectionMode;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.InternalOrder;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceParserHelper;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class RareTermsAggregationBuilder extends ValuesSourceAggregationBuilder<ValuesSource, RareTermsAggregationBuilder> {
    public static final String NAME = "rare_terms";

    public static final ParseField EXECUTION_HINT_FIELD_NAME = new ParseField("execution_hint");
    public static final ParseField MAX_DOC_COUNT_FIELD_NAME = new ParseField("max_doc_count");

    private static final int MAX_MAX_DOC_COUNT = 10;
    private static final ObjectParser<RareTermsAggregationBuilder, Void> PARSER;
    static {
        PARSER = new ObjectParser<>(RareTermsAggregationBuilder.NAME);
        ValuesSourceParserHelper.declareAnyFields(PARSER, true, true);
        PARSER.declareLong(RareTermsAggregationBuilder::maxDocCount, MAX_DOC_COUNT_FIELD_NAME);

        PARSER.declareString(RareTermsAggregationBuilder::executionHint, EXECUTION_HINT_FIELD_NAME);

        PARSER.declareField((b, v) -> b.includeExclude(IncludeExclude.merge(v, b.includeExclude())),
            IncludeExclude::parseInclude, IncludeExclude.INCLUDE_FIELD, ObjectParser.ValueType.OBJECT_ARRAY_OR_STRING);

        PARSER.declareField((b, v) -> b.includeExclude(IncludeExclude.merge(b.includeExclude(), v)),
            IncludeExclude::parseExclude, IncludeExclude.EXCLUDE_FIELD, ObjectParser.ValueType.STRING_ARRAY);
    }

    public static AggregationBuilder parse(String aggregationName, XContentParser parser) throws IOException {
        return PARSER.parse(parser, new RareTermsAggregationBuilder(aggregationName, null), null);
    }

    private IncludeExclude includeExclude = null;
    private String executionHint = null;
    private int maxDocCount = 1;

    public RareTermsAggregationBuilder(String name, ValueType valueType) {
        super(name, ValuesSourceType.ANY, valueType);
    }

    protected RareTermsAggregationBuilder(RareTermsAggregationBuilder clone, Builder factoriesBuilder, Map<String, Object> metaData) {
        super(clone, factoriesBuilder, metaData);
        this.executionHint = clone.executionHint;
        this.includeExclude = clone.includeExclude;
    }

    @Override
    protected AggregationBuilder shallowCopy(Builder factoriesBuilder, Map<String, Object> metaData) {
        return new RareTermsAggregationBuilder(this, factoriesBuilder, metaData);
    }

    /**
     * Read from a stream.
     */
    public RareTermsAggregationBuilder(StreamInput in) throws IOException {
        super(in, ValuesSourceType.ANY);
        executionHint = in.readOptionalString();
        includeExclude = in.readOptionalWriteable(IncludeExclude::new);
        maxDocCount = in.readVInt();
    }

    @Override
    protected boolean serializeTargetValueType() {
        return true;
    }

    @Override
    protected void innerWriteTo(StreamOutput out) throws IOException {
        out.writeOptionalString(executionHint);
        out.writeOptionalWriteable(includeExclude);
        out.writeVInt(maxDocCount);
    }

    /**
     * Set the maximum document count terms should have in order to appear in
     * the response.
     */
    public RareTermsAggregationBuilder maxDocCount(long maxDocCount) {
        if (maxDocCount <= 0) {
            throw new IllegalArgumentException(
                "[" + MAX_DOC_COUNT_FIELD_NAME.getPreferredName() + "] must be greater than 0. Found ["
                    + maxDocCount + "] in [" + name + "]");
        }
        //TODO review: what size cap should we put on this?
        if (maxDocCount > MAX_MAX_DOC_COUNT) {
            throw new IllegalArgumentException("[" + MAX_DOC_COUNT_FIELD_NAME.getPreferredName() + "] must be smaller" +
                "than " + MAX_MAX_DOC_COUNT + "in [" + name + "]");
        }
        this.maxDocCount = (int) maxDocCount;
        return this;
    }

    /**
     * Expert: sets an execution hint to the aggregation.
     */
    public RareTermsAggregationBuilder executionHint(String executionHint) {
        this.executionHint = executionHint;
        return this;
    }

    /**
     * Expert: gets an execution hint to the aggregation.
     */
    public String executionHint() {
        return executionHint;
    }

    /**
     * Set terms to include and exclude from the aggregation results
     */
    public RareTermsAggregationBuilder includeExclude(IncludeExclude includeExclude) {
        this.includeExclude = includeExclude;
        return this;
    }

    /**
     * Get terms to include and exclude from the aggregation results
     */
    public IncludeExclude includeExclude() {
        return includeExclude;
    }

    @Override
    protected ValuesSourceAggregatorFactory<ValuesSource, ?> innerBuild(SearchContext context, ValuesSourceConfig<ValuesSource> config,
                                                                        AggregatorFactory<?> parent, Builder subFactoriesBuilder) throws IOException {
        return new RareTermsAggregatorFactory(name, config, includeExclude, executionHint,
            context, parent, subFactoriesBuilder, metaData, maxDocCount);
    }

    @Override
    protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        if (executionHint != null) {
            builder.field(RareTermsAggregationBuilder.EXECUTION_HINT_FIELD_NAME.getPreferredName(), executionHint);
        }
        if (includeExclude != null) {
            includeExclude.toXContent(builder, params);
        }
        builder.field(MAX_DOC_COUNT_FIELD_NAME.getPreferredName(), maxDocCount);
        return builder;
    }

    @Override
    protected int innerHashCode() {
        return Objects.hash(executionHint, includeExclude, maxDocCount);
    }

    @Override
    protected boolean innerEquals(Object obj) {
        RareTermsAggregationBuilder other = (RareTermsAggregationBuilder) obj;
        return Objects.equals(executionHint, other.executionHint)
            && Objects.equals(includeExclude, other.includeExclude)
            && Objects.equals(maxDocCount, other.maxDocCount);
    }

    @Override
    public String getType() {
        return NAME;
    }

}
