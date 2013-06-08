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

package org.elasticsearch.search.aggregations.format;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.index.mapper.ip.IpFieldMapper;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * A strategy for formatting time represented as millis long value to string
 */
public interface ValueFormatter extends Streamable {

    public final static ValueFormatter NULL = new Null();
    public final static ValueFormatter RAW = new Raw();
    public final static ValueFormatter IPv4 = new IP4();

    byte id();

    /**
     * Formats the given millis time value (since the epoch) to string.
     *
     * @param value The long value to format.
     * @return      The formatted value as string.
     */
    String format(long value);

    /**
     * The 
     * @param value double The double value to format.
     * @return      The formatted value as string
     */
    String format(double value);



    /**
     * A time formatter that doesn't do any formatting and returns {@code null} value instead.
     */
    static class Null implements ValueFormatter {

        static final byte ID = 0;

        @Override
        public String format(long time) {
            return null;
        }

        @Override
        public String format(double value) {
            return null;
        }

        @Override
        public byte id() {
            return ID;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }
    }

    static class Raw implements ValueFormatter {

        static final byte ID = 1;

        @Override
        public String format(long value) {
            return String.valueOf(value);
        }

        @Override
        public String format(double value) {
            return String.valueOf(value);
        }

        @Override
        public byte id() {
            return ID;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }
    }

    /**
     * A time formatter which is based on date/time format.
     */
    public static class DateTime implements ValueFormatter {

        static final byte ID = 2;

        String pattern;
        DateTimeFormatter formatter;

        DateTime() {} // for serialization

        public DateTime(String pattern) {
            this.pattern = pattern;
            this.formatter = DateTimeFormat.forPattern(pattern);
        }

        @Override
        public String format(long time) {
            return formatter.print(time);
        }
        
        public String format(double value) {
            return String.valueOf(value); // should never be called
        }

        @Override
        public byte id() {
            return ID;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            pattern = in.readString();
            formatter = DateTimeFormat.forPattern(pattern);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(pattern);
        }
    }

    public static abstract class Number implements ValueFormatter {

        NumberFormat format;

        Number() {} // for serialization

        Number(NumberFormat format) {
            this.format = format;
        }

        @Override
        public String format(long value) {
            return format.format(value);
        }

        @Override
        public String format(double value) {
            return format.format(value);
        }

        public static class Locale extends Number {

            static final byte ID = 3;

            String locale;

            Locale() {} // for serialization

            public Locale(String locale) {
                super(DecimalFormat.getInstance(java.util.Locale.forLanguageTag(locale)));
                this.locale = locale;
            }

            @Override
            public byte id() {
                return ID;
            }

            @Override
            public void readFrom(StreamInput in) throws IOException {
                locale = in.readString();
                format = DecimalFormat.getInstance(java.util.Locale.forLanguageTag(locale));
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                out.writeString(locale);
            }
        }

        public static class Pattern extends Number {

            static final byte ID = 4;

            String pattern;

            Pattern() {} // for serialization

            public Pattern(String pattern) {
                super(new DecimalFormat(pattern));
                this.pattern = pattern;
            }

            @Override
            public byte id() {
                return ID;
            }

            @Override
            public void readFrom(StreamInput in) throws IOException {
                pattern = in.readString();
                format = new DecimalFormat(pattern);
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                out.writeString(pattern);
            }
        }

        public static class Currency extends Number {

            static final byte ID = 5;

            String locale;

            Currency() {} // for serialization

            public Currency(String locale) {
                super(DecimalFormat.getCurrencyInstance(java.util.Locale.forLanguageTag(locale)));
                this.locale = locale;
            }

            @Override
            public byte id() {
                return ID;
            }

            @Override
            public void readFrom(StreamInput in) throws IOException {
                locale = in.readString();
                format = DecimalFormat.getCurrencyInstance(java.util.Locale.forLanguageTag(locale));
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                out.writeString(locale);
            }
        }
    }

    public static class IP4 implements ValueFormatter {

        static final byte ID = 6;

        @Override
        public byte id() {
            return ID;
        }

        @Override
        public String format(long value) {
            return IpFieldMapper.longToIp(value);
        }

        @Override
        public String format(double value) {
            return format((long) value);
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }
    }


    
    
}
