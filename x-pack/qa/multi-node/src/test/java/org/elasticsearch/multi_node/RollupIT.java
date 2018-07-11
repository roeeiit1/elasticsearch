/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.multi_node;

import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.core.rollup.job.RollupJob;
import org.elasticsearch.xpack.core.watcher.support.xcontent.ObjectPath;
import org.hamcrest.Matcher;
import org.junit.After;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken.basicAuthHeaderValue;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

public class RollupIT extends ESRestTestCase {

    @Override
    protected Settings restClientSettings() {
        return getClientSettings("super-user", "x-pack-super-password");
    }

    @Override
    protected Settings restAdminSettings() {
        return getClientSettings("super-user", "x-pack-super-password");
    }

    private Settings getClientSettings(final String username, final String password) {
        final String token = basicAuthHeaderValue(username, new SecureString(password.toCharArray()));
        return Settings.builder().put(ThreadContext.PREFIX + ".Authorization", token).build();
    }

    static Map<String, Object> toMap(Response response) throws IOException {
        return toMap(EntityUtils.toString(response.getEntity()));
    }

    static Map<String, Object> toMap(String response) throws IOException {
        return XContentHelper.convertToMap(JsonXContent.jsonXContent, response, false);
    }

    @After
    public void clearRollupMetadata() throws Exception {
        deleteAllJobs();
        waitForPendingTasks();
        // indices will be deleted by the ESRestTestCase class
    }

    public void testBigRollup() throws Exception {
        final int numDocs = 200;

        // index documents for the rollup job
        final StringBuilder bulk = new StringBuilder();
        for (int i = 0; i < numDocs; i++) {
            bulk.append("{\"index\":{\"_index\":\"rollup-docs\",\"_type\":\"_doc\"}}\n");
            ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1531221196 + (60*i)), ZoneId.of("UTC"));
            String date = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            bulk.append("{\"timestamp\":\"").append(date).append("\",\"value\":").append(i).append("}\n");
        }
        bulk.append("\r\n");

        final Request bulkRequest = new Request("POST", "/_bulk");
        bulkRequest.addParameter("refresh", "true");
        bulkRequest.setJsonEntity(bulk.toString());
        client().performRequest(bulkRequest);
        // create the rollup job
        final Request createRollupJobRequest = new Request("PUT", "/_xpack/rollup/job/rollup-job-test");
        createRollupJobRequest.setJsonEntity("{"
            + "\"index_pattern\":\"rollup-*\","
            + "\"rollup_index\":\"results-rollup\","
            + "\"cron\":\"*/1 * * * * ?\","             // fast cron and big page size so test runs quickly
            + "\"page_size\":200,"
            + "\"groups\":{"
            + "    \"date_histogram\":{"
            + "        \"field\":\"timestamp\","
            + "        \"interval\":\"5m\""
            + "      }"
            + "},"
            + "\"metrics\":["
            + "    {\"field\":\"value\",\"metrics\":[\"min\",\"max\",\"sum\"]}"
            + "]"
            + "}");

        Map<String, Object> createRollupJobResponse = toMap(client().performRequest(createRollupJobRequest));
        assertThat(createRollupJobResponse.get("acknowledged"), equalTo(Boolean.TRUE));

        // start the rollup job
        final Request startRollupJobRequest = new Request("POST", "_xpack/rollup/job/rollup-job-test/_start");
        Map<String, Object> startRollupJobResponse = toMap(client().performRequest(startRollupJobRequest));
        assertThat(startRollupJobResponse.get("started"), equalTo(Boolean.TRUE));

        assertRollUpJob("rollup-job-test");

        // Wait for the job to finish, by watching how many rollup docs we've indexed
        assertBusy(() -> {
            final Request getRollupJobRequest = new Request("GET", "_xpack/rollup/job/rollup-job-test");
            Response getRollupJobResponse = client().performRequest(getRollupJobRequest);
            assertThat(getRollupJobResponse.getStatusLine().getStatusCode(), equalTo(RestStatus.OK.getStatus()));

            Map<String, Object> job = getJob(getRollupJobResponse, "rollup-job-test");
            if (job != null) {
                assertThat(ObjectPath.eval("status.job_state", job), equalTo("started"));
                assertThat(ObjectPath.eval("stats.rollups_indexed", job), equalTo(41));
            }
        }, 30L, TimeUnit.SECONDS);

        // Refresh the rollup index to make sure all newly indexed docs are searchable
        final Request refreshRollupIndex = new Request("POST", "results-rollup/_refresh");
        toMap(client().performRequest(refreshRollupIndex));

        String jsonRequestBody = "{\n" +
            "  \"size\": 0,\n" +
            "  \"query\": {\n" +
            "    \"match_all\": {}\n" +
            "  },\n" +
            "  \"aggs\": {\n" +
            "    \"date_histo\": {\n" +
            "      \"date_histogram\": {\n" +
            "        \"field\": \"timestamp\",\n" +
            "        \"interval\": \"1h\"\n" +
            "      },\n" +
            "      \"aggs\": {\n" +
            "        \"the_max\": {\n" +
            "          \"max\": {\n" +
            "            \"field\": \"value\"\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        Request request = new Request("GET", "rollup-docs/_search");
        request.setJsonEntity(jsonRequestBody);
        Response liveResponse = client().performRequest(request);
        Map<String, Object> liveBody = toMap(liveResponse);

        request = new Request("GET", "results-rollup/_rollup_search");
        request.setJsonEntity(jsonRequestBody);
        Response rollupResponse = client().performRequest(request);
        Map<String, Object> rollupBody = toMap(rollupResponse);

        // Do the live agg results match the rollup agg results?
        assertThat(ObjectPath.eval("aggregations.date_histo.buckets", liveBody),
            equalTo(ObjectPath.eval("aggregations.date_histo.buckets", rollupBody)));

        request = new Request("GET", "rollup-docs/_rollup_search");
        request.setJsonEntity(jsonRequestBody);
        Response liveRollupResponse = client().performRequest(request);
        Map<String, Object> liveRollupBody = toMap(liveRollupResponse);

        // Does searching the live index via rollup_search work match the live search?
        assertThat(ObjectPath.eval("aggregations.date_histo.buckets", liveBody),
            equalTo(ObjectPath.eval("aggregations.date_histo.buckets", liveRollupBody)));

    }

    @SuppressWarnings("unchecked")
    private void assertRollUpJob(final String rollupJob) throws Exception {
        final Matcher<?> expectedStates = anyOf(equalTo("indexing"), equalTo("started"));
        waitForRollUpJob(rollupJob, expectedStates);

        // check that the rollup job is started using the RollUp API
        final Request getRollupJobRequest = new Request("GET", "_xpack/rollup/job/" + rollupJob);
        Map<String, Object> getRollupJobResponse = toMap(client().performRequest(getRollupJobRequest));
        Map<String, Object> job = getJob(getRollupJobResponse, rollupJob);
        if (job != null) {
            assertThat(ObjectPath.eval("status.job_state", job), expectedStates);
        }

        // check that the rollup job is started using the Tasks API
        final Request taskRequest = new Request("GET", "_tasks");
        taskRequest.addParameter("detailed", "true");
        taskRequest.addParameter("actions", "xpack/rollup/*");
        Map<String, Object> taskResponse = toMap(client().performRequest(taskRequest));
        Map<String, Object> taskResponseNodes = (Map<String, Object>) taskResponse.get("nodes");
        Map<String, Object> taskResponseNode = (Map<String, Object>) taskResponseNodes.values().iterator().next();
        Map<String, Object> taskResponseTasks = (Map<String, Object>) taskResponseNode.get("tasks");
        Map<String, Object> taskResponseStatus = (Map<String, Object>) taskResponseTasks.values().iterator().next();
        assertThat(ObjectPath.eval("status.job_state", taskResponseStatus), expectedStates);

        // check that the rollup job is started using the Cluster State API
        final Request clusterStateRequest = new Request("GET", "_cluster/state/metadata");
        Map<String, Object> clusterStateResponse = toMap(client().performRequest(clusterStateRequest));
        List<Map<String, Object>> rollupJobTasks = ObjectPath.eval("metadata.persistent_tasks.tasks", clusterStateResponse);

        boolean hasRollupTask = false;
        for (Map<String, Object> task : rollupJobTasks) {
            if (ObjectPath.eval("id", task).equals(rollupJob)) {
                hasRollupTask = true;

                final String jobStateField = "task.xpack/rollup/job.state.job_state";
                assertThat("Expected field [" + jobStateField + "] to be started or indexing in " + task.get("id"),
                    ObjectPath.eval(jobStateField, task), expectedStates);
                break;
            }
        }
        if (hasRollupTask == false) {
            fail("Expected persistent task for [" + rollupJob + "] but none found.");
        }

    }

    private void waitForRollUpJob(final String rollupJob, final Matcher<?> expectedStates) throws Exception {
        assertBusy(() -> {
            final Request getRollupJobRequest = new Request("GET", "_xpack/rollup/job/" + rollupJob);
            Response getRollupJobResponse = client().performRequest(getRollupJobRequest);
            assertThat(getRollupJobResponse.getStatusLine().getStatusCode(), equalTo(RestStatus.OK.getStatus()));

            Map<String, Object> job = getJob(getRollupJobResponse, rollupJob);
            if (job != null) {
                assertThat(ObjectPath.eval("status.job_state", job), expectedStates);
            }
        }, 30L, TimeUnit.SECONDS);
    }

    private Map<String, Object> getJob(Response response, String targetJobId) throws IOException {
        return getJob(ESRestTestCase.entityAsMap(response), targetJobId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJob(Map<String, Object> jobsMap, String targetJobId) throws IOException {

        List<Map<String, Object>> jobs =
            (List<Map<String, Object>>) XContentMapValues.extractValue("jobs", jobsMap);

        if (jobs == null) {
            return null;
        }

        for (Map<String, Object> job : jobs) {
            String jobId = (String) ((Map<String, Object>) job.get("config")).get("id");
            if (jobId.equals(targetJobId)) {
                return job;
            }
        }
        return null;
    }

    private void waitForPendingTasks() throws Exception {
        ESTestCase.assertBusy(() -> {
            try {
                Request request = new Request("GET", "/_cat/tasks");
                request.addParameter("detailed", "true");
                Response response = adminClient().performRequest(request);
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    try (BufferedReader responseReader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                        int activeTasks = 0;
                        String line;
                        StringBuilder tasksListString = new StringBuilder();
                        while ((line = responseReader.readLine()) != null) {

                            // We only care about Rollup jobs, otherwise this fails too easily due to unrelated tasks
                            if (line.startsWith(RollupJob.NAME) == true) {
                                activeTasks++;
                                tasksListString.append(line);
                                tasksListString.append('\n');
                            }
                        }
                        assertEquals(activeTasks + " active tasks found:\n" + tasksListString, 0, activeTasks);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError("Error getting active tasks list", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void deleteAllJobs() throws Exception {
        Request request = new Request("GET", "/_xpack/rollup/job/_all");
        Response response = adminClient().performRequest(request);
        Map<String, Object> jobs = ESRestTestCase.entityAsMap(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jobConfigs =
            (List<Map<String, Object>>) XContentMapValues.extractValue("jobs", jobs);

        if (jobConfigs == null) {
            return;
        }

        for (Map<String, Object> jobConfig : jobConfigs) {
            logger.debug(jobConfig);
            String jobId = (String) ((Map<String, Object>) jobConfig.get("config")).get("id");
            logger.debug("Deleting job " + jobId);
            try {
                request = new Request("DELETE", "/_xpack/rollup/job/" + jobId);
                adminClient().performRequest(request);
            } catch (Exception e) {
                // ok
            }
        }
    }

    private static String responseEntityToString(Response response) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
