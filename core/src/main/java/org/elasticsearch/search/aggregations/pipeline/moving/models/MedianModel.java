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

package org.elasticsearch.search.aggregations.pipeline.moving.models;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MedianModel extends MovModel {
    public static final String NAME = "median";

    public MedianModel() {
    }

    /**
     * Read from a stream.
     */
    public MedianModel(StreamInput in) throws IOException {
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        // Nothing to write
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public MovModel clone() {
        return new MedianModel();
    }

    @Override
    public double next(Collection<Double> values) {
        List<Double> listValues = new ArrayList<>(values);
        Collections.sort(listValues);
        if (values.size() % 2 == 0) {
            int middle = (int)Math.floor(values.size()/2.0);
            return (listValues.get(middle - 1) + listValues.get(middle) ) / 2;
        }

        return listValues.get(listValues.size() / 2);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(MovModel.FUNCTION.getPreferredName(), NAME);
        return builder;
    }

    public static final AbstractModelParser PARSER = new AbstractModelParser() {
        @Override
        public MovModel parse(@Nullable Map<String, Object> settings, String pipelineName, int windowSize) throws ParseException {
            checkUnrecognizedParams(settings);
            return new MedianModel();
        }
    };

    public static class MedianModelBuilder implements MovModelBuilder {
        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field(MovModel.FUNCTION.getPreferredName(), NAME);
            return builder;
        }

        @Override
        public MovModel build() {
            return new MedianModel();
        }
    }

}
