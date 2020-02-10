/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.eql.common;

import org.elasticsearch.xpack.ql.tree.Location;
import org.elasticsearch.xpack.ql.tree.Node;
import org.elasticsearch.xpack.ql.util.StringUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.elasticsearch.common.logging.LoggerMessageFormat.format;

public class Failure {

    private final Node<?> node;
    private final String message;

    public Failure(Node<?> node, String message) {
        this.node = node;
        this.message = message;
    }

    public Node<?> node() {
        return node;
    }

    public String message() {
        return message;
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, node);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Failure other = (Failure) obj;
        return Objects.equals(message, other.message) && Objects.equals(node, other.node);
    }

    @Override
    public String toString() {
        return message;
    }

    public static Failure fail(Node<?> source, String message, Object... args) {
        return new Failure(source, format(message, args));
    }

    public static String failMessage(Collection<Failure> failures) {
        return failures.stream().map(f -> {
            Location l = f.node().source().source();
            return "line " + l.getLineNumber() + ":" + l.getColumnNumber() + ": " + f.message();
        }).collect(Collectors.joining(StringUtils.NEW_LINE,
                format("Found {} problem{}\n", failures.size(), failures.size() > 1 ? "s" : StringUtils.EMPTY), 
                StringUtils.EMPTY));
    }
}
