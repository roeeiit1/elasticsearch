/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.core.datascience;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.core.XPackFeatureSet;
import org.elasticsearch.xpack.core.XPackField;

import java.io.IOException;
import java.util.Objects;

public class DataScienceFeatureSetUsage extends XPackFeatureSet.Usage {

    public DataScienceFeatureSetUsage(boolean available, boolean enabled) {
        super(XPackField.DATA_SCIENCE, available, enabled);
    }

    public DataScienceFeatureSetUsage(StreamInput input) throws IOException {
        super(input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available, enabled);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DataScienceFeatureSetUsage other = (DataScienceFeatureSetUsage) obj;
        return Objects.equals(available, other.available) &&
            Objects.equals(enabled, other.enabled);
    }
}
