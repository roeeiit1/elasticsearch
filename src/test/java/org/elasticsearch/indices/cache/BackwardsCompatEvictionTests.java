package org.elasticsearch.indices.cache;

import org.apache.lucene.util.English;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.metrics.EvictionStats;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ElasticsearchBackwardsCompatIntegrationTest;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@ElasticsearchIntegrationTest.ClusterScope(scope= ElasticsearchIntegrationTest.Scope.SUITE, numClientNodes = 0)
public class BackwardsCompatEvictionTests extends ElasticsearchBackwardsCompatIntegrationTest {
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        // Set the fielddata and filter size to 1b and expire to 1ms, forces evictions immediately
        return  ImmutableSettings.settingsBuilder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("indices.fielddata.cache.expire", "1ms")
                .put("indices.fielddata.cache.size", "100b")
                .put("indices.cache.filter.expire", "1ms")
                .put("indices.cache.filter.size", "100b")
                .build();
    }

    @Override
    protected Settings externalNodeSettings(int nodeOrdinal) {
        // Set the fielddata and filter size to 1b and expire to 1ms, forces evictions immediately
        return  ImmutableSettings.settingsBuilder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("indices.fielddata.cache.expire", "1ms")
                .put("indices.fielddata.cache.size", "100b")
                .put("indices.cache.filter.expire", "1ms")
                .put("indices.cache.filter.size", "100b")
                .build();
    }


    @Test
    public void testFieldDataEvictions() throws Exception {
        createIndex("test");

        NodesInfoResponse nodesInfo = client().admin().cluster().prepareNodesInfo().execute().actionGet();
        Map<String, NodeInfo> versions = nodesInfo.getNodesMap();

        Settings settings = ImmutableSettings.settingsBuilder()
                .put("client.transport.ignore_cluster_name", true).build();

        // We explicitly connect to each node with a custom TransportClient
        for (NodeInfo n : nodesInfo.getNodes()) {
            TransportClient tc = new TransportClient(settings).addTransportAddress(n.getNode().address());
            NodesStatsResponse ns = tc.admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

            // This is the version of the node we are talking to via Transport Client
            Version tcNodeVersion = versions.get(n.getNode().getId()).getVersion();

            for (NodeStats stats : ns.getNodes()) {

                // If the node we are talking to is 1.3.0+, it will have full responses
                if (tcNodeVersion.onOrAfter(Version.V_1_3_0)) {

                    EvictionStats ev = stats.getIndices().getFieldData().getEvictionStats();
                    Version nodeVersion = versions.get(stats.getNode().getId()).getVersion();

                    // If the node in the stats (not the node we are talking to) is 1.3.0+, it will have eviction rates
                    if (nodeVersion.onOrAfter(Version.V_1_3_0)) {
                        assertThat(ev.getEvictions(), equalTo(0l));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(0D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(0D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(0D));
                    } else {
                        // Otherwise it will have negative one's for rates
                        assertThat(ev.getEvictions(), equalTo(0l));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                    }
                } else {
                    // If the node we are talking to is < 1.3.0, it will only have eviction counts and no rates.
                    // But because this test code is executing in 1.3.0+, evictions will be present in response and negative
                    EvictionStats ev = stats.getIndices().getFieldData().getEvictionStats();
                    assertThat(ev.getEvictions(), equalTo(0l));
                    assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                }
            }
        }

        int numDocs = randomIntBetween(500, 5000);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);
        ensureGreen();

        // sort to load it to field data...run multiple queries to thrash the evictions
        for (int i = 0; i < 100; i++) {
            client().prepareSearch().addSort("field1", SortOrder.ASC).execute().actionGet();
            client().prepareSearch().addSort("field2", SortOrder.ASC).execute().actionGet();
        }

        // Just to give enough time for evictions to occur
        Thread.sleep(2000);

        // We explicitly connect to each node with a custom TransportClient
        for (NodeInfo n : nodesInfo.getNodes()) {
            TransportClient tc = new TransportClient(settings).addTransportAddress(n.getNode().address());
            NodesStatsResponse ns = tc.admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

            // This is the version of the node we are talking to via Transport Client
            Version tcNodeVersion = versions.get(n.getNode().getId()).getVersion();

            for (NodeStats stats : ns.getNodes()) {
                boolean hasDocs = stats.getIndices().getDocs().getCount() > 0;

                // If the node we are talking to is 1.3.0+, it will have full responses
                if (tcNodeVersion.onOrAfter(Version.V_1_3_0)) {

                    EvictionStats ev = stats.getIndices().getFieldData().getEvictionStats();
                    Version nodeVersion = versions.get(stats.getNode().getId()).getVersion();

                    // If the node in the stats (not the node we are talking to) is 1.3.0+, it will have eviction rates
                    if (nodeVersion.onOrAfter(Version.V_1_3_0)) {
                        assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                        assertThat(ev.getEvictionsOneMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                    } else {
                        // Otherwise it will have negative one's for rates
                        assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                    }
                } else {
                    // If the node we are talking to is < 1.3.0, it will only have eviction counts and no rates.
                    // But because this test code is executing in 1.3.0+, evictions will be present in response and negative
                    EvictionStats ev = stats.getIndices().getFieldData().getEvictionStats();
                    assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                    assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                }
            }
        }
    }

    @Test
    public void testFilterEvictions() throws Exception {
        createIndex("test");

        NodesInfoResponse nodesInfo = client().admin().cluster().prepareNodesInfo().execute().actionGet();
        Map<String, NodeInfo> versions = nodesInfo.getNodesMap();

        Settings settings = ImmutableSettings.settingsBuilder()
                .put("client.transport.ignore_cluster_name", true).build();

        // We explicitly connect to each node with a custom TransportClient
        for (NodeInfo n : nodesInfo.getNodes()) {
            TransportClient tc = new TransportClient(settings).addTransportAddress(n.getNode().address());
            NodesStatsResponse ns = tc.admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

            // This is the version of the node we are talking to via Transport Client
            Version tcNodeVersion = versions.get(n.getNode().getId()).getVersion();

            for (NodeStats stats : ns.getNodes()) {

                // If the node we are talking to is 1.3.0+, it will have full responses
                if (tcNodeVersion.onOrAfter(Version.V_1_3_0)) {

                    EvictionStats ev = stats.getIndices().getFilterCache().getEvictionStats();
                    Version nodeVersion = versions.get(stats.getNode().getId()).getVersion();

                    // If the node in the stats (not the node we are talking to) is 1.3.0+, it will have eviction rates
                    if (nodeVersion.onOrAfter(Version.V_1_3_0)) {
                        assertThat(ev.getEvictions(), equalTo(0l));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(0D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(0D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(0D));
                    } else {
                        // Otherwise it will have negative one's for rates
                        assertThat(ev.getEvictions(), equalTo(0l));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                    }
                } else {
                    // If the node we are talking to is < 1.3.0, it will only have eviction counts and no rates.
                    // But because this test code is executing in 1.3.0+, evictions will be present in response and negative
                    EvictionStats ev = stats.getIndices().getFilterCache().getEvictionStats();
                    assertThat(ev.getEvictions(), equalTo(0l));
                    assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                }
            }
        }


        int numDocs = randomIntBetween(500, 1000);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);
        ensureGreen();

        // Run some searches to cache and evict filters
        for (int i = 0; i < 100; i++) {
            client().prepareSearch().setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter("field1", English.intToEnglish(i)))).execute().actionGet();
            client().prepareSearch().setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter("field2", i))).execute().actionGet();
        }

        // Just to give enough time for evictions to occur
        Thread.sleep(2000);

        // We explicitly connect to each node with a custom TransportClient
        for (NodeInfo n : nodesInfo.getNodes()) {
            TransportClient tc = new TransportClient(settings).addTransportAddress(n.getNode().address());
            NodesStatsResponse ns = tc.admin().cluster().prepareNodesStats().setIndices(true).execute().actionGet();

            // This is the version of the node we are talking to via Transport Client
            Version tcNodeVersion = versions.get(n.getNode().getId()).getVersion();

            for (NodeStats stats : ns.getNodes()) {
                boolean hasDocs = stats.getIndices().getDocs().getCount() > 0;

                // If the node we are talking to is 1.3.0+, it will have full responses
                if (tcNodeVersion.onOrAfter(Version.V_1_3_0)) {

                    EvictionStats ev = stats.getIndices().getFilterCache().getEvictionStats();
                    Version nodeVersion = versions.get(stats.getNode().getId()).getVersion();

                    // If the node in the stats (not the node we are talking to) is 1.3.0+, it will have eviction rates
                    if (nodeVersion.onOrAfter(Version.V_1_3_0)) {
                        assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                        assertThat(ev.getEvictionsOneMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), hasDocs ? greaterThan(0D) : equalTo(0D));
                    } else {
                        // Otherwise it will have negative one's for rates
                        assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                        assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                        assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                    }
                } else {
                    // If the node we are talking to is < 1.3.0, it will only have eviction counts and no rates.
                    // But because this test code is executing in 1.3.0+, evictions will be present in response and negative
                    EvictionStats ev = stats.getIndices().getFilterCache().getEvictionStats();
                    assertThat(ev.getEvictions(), hasDocs ? greaterThan(0l) : equalTo(0L));
                    assertThat(ev.getEvictionsOneMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFiveMinuteRate(), equalTo(-1D));
                    assertThat(ev.getEvictionsFifteenMinuteRate(), equalTo(-1D));
                }
            }
        }
    }
}
