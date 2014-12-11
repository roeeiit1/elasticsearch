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

package org.elasticsearch.common.lucene.search.profile;

import com.google.common.base.Stopwatch;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * This class times the execution of the subquery that it wraps.  Timing includes:
 *  - ProfileQuery.createWeight
 *
 *  - ProfileWeight.getValueForNormalization
 *  - ProfileWeight.normalize
 *
 *  - ProfileScorer.advance
 *  - ProfileScorer.nextDoc
 *  - ProfileScorer.score
 *
 *  A ProfileQuery maintains it's own timing independent of the rest of the query.
 *  It must be later aggregated together using Profile.collapse
 */
public class ProfileQuery extends Query implements ProfileComponent {

    Query subQuery;
    private long time = 0;

    private String className;
    private String details;
    private Stopwatch stopwatch;

    public ProfileQuery(Query subQuery) {
        this.subQuery = subQuery;
        this.setClassName(subQuery.getClass().getSimpleName());
        this.setDetails(subQuery.toString());
        this.stopwatch = Stopwatch.createUnstarted();
    }

    public Query subQuery() {
        return subQuery;
    }

    public long time() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void addTime(long time) {
        this.time += time;
    }

    public String className() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String details() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public void setBoost(float b) {
        this.subQuery.setBoost(b);
    }

    public float getBoost() {
        return this.subQuery.getBoost();
    }


    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        stopwatch.start();
        Query rewrittenQuery = subQuery.rewrite(reader);
        stopwatch.stop();
        addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
        stopwatch.reset();

        if (rewrittenQuery == subQuery) {
            return this;
        }

        // The rewriting process can potentially add many new nested components
        // Perform a walk of the rewritten query to wrap all the new parts
        ProfileQueryVisitor walker = new ProfileQueryVisitor();
        rewrittenQuery = (ProfileQuery) walker.apply(rewrittenQuery);

        ProfileQuery newProfile = new ProfileQuery(rewrittenQuery);
        newProfile.setTime(this.time());
        return newProfile;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        subQuery.extractTerms(terms);
    }

    @Override
    public Query clone() {
        return new ProfileQuery(subQuery.clone());
    }

    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException {
        long threadId = Thread.currentThread().getId();
        System.out.println(threadId + " >> " + subQuery.getClass());
        stopwatch.start();
        Weight subQueryWeight = subQuery.createWeight(searcher);
        stopwatch.stop();
        addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
        stopwatch.reset();

        return new ProfileWeight(subQueryWeight, this);
    }

    class ProfileWeight extends Weight {

        final Weight subQueryWeight;
        private ProfileQuery profileQuery;
        private Stopwatch stopwatch;

        public ProfileWeight(Weight subQueryWeight, ProfileQuery profileQuery) throws IOException {
            this.subQueryWeight = subQueryWeight;
            this.profileQuery = profileQuery;
            this.stopwatch = Stopwatch.createUnstarted();
        }

        public Query getQuery() {
            return ProfileQuery.this;
        }

        public void addTime(long time) {
            this.profileQuery.addTime(time);
        }

        @Override
        public float getValueForNormalization() throws IOException {
            stopwatch.start();
            float sum = subQueryWeight.getValueForNormalization();
            stopwatch.stop();
            addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
            stopwatch.reset();

            return sum;
        }

        @Override
        public void normalize(float norm, float topLevelBoost) {
            stopwatch.start();
            subQueryWeight.normalize(norm, topLevelBoost * getBoost());
            stopwatch.stop();
            addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
            stopwatch.reset();
        }

        @Override
        public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
            Scorer subQueryScorer = subQueryWeight.scorer(context, acceptDocs);
            if (subQueryScorer == null) {
                return null;
            }

            return new ProfileScorer(this, subQueryScorer);
        }

        @Override
        public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
            Explanation subQueryExpl = subQueryWeight.explain(context, doc);
            return subQueryExpl;

        }
    }

    static class ProfileScorer extends Scorer {

        private final Scorer scorer;
        private ProfileWeight profileWeight;
        private Stopwatch stopwatch;

        private ProfileScorer(ProfileWeight w, Scorer scorer) throws IOException {
            super(w);
            this.scorer = scorer;
            this.profileWeight = w;
            this.stopwatch = Stopwatch.createUnstarted();
        }

        public void addTime(long time) {
            this.profileWeight.addTime(time);
        }

        @Override
        public int docID() {
            return scorer.docID();
        }

        @Override
        public int advance(int target) throws IOException {
            stopwatch.start();
            int id = scorer.advance(target);
            stopwatch.stop();
            addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
            stopwatch.reset();
            return id;
        }

        @Override
        public int nextDoc() throws IOException {
            stopwatch.start();
            int docId = scorer.nextDoc();
            stopwatch.stop();
            addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
            stopwatch.reset();

            return docId;
        }

        @Override
        public float score() throws IOException {
            stopwatch.start();
            float score = scorer.score();
            stopwatch.stop();
            addTime(stopwatch.elapsed(TimeUnit.MICROSECONDS));
            stopwatch.reset();

            return score;
        }

        @Override
        public int freq() throws IOException {
            return scorer.freq();
        }

        @Override
        public long cost() {
            return scorer.cost();
        }
    }

    public String toString(String field) {
        StringBuilder sb = new StringBuilder();

        // Currently only outputting the subquery's string.  This makes the ProfileQuery "invisible"
        // in explains/analyze, but makes the output much nicer for profiling
        sb.append(subQuery.toString(field));
        return sb.toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProfileQuery other = (ProfileQuery) o;
        return this.className.equals(other.className)
                && this.details.equals(other.details)
                && this.subQuery.equals(other.subQuery);
    }

    public int hashCode() {
        final int prime = 31;
        int result = prime * 19 + this.className.hashCode();
        result = prime * result + this.details.hashCode();
        result = prime * result + this.subQuery.hashCode();
        return result;

        //return subQuery.hashCode() + 31 *  Float.floatToIntBits(getBoost());
    }
}
