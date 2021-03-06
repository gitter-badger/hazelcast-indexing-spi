package com.hazelcast.cluster;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import com.hazelcast.instance.HazelcastInstanceManager;
import com.hazelcast.instance.Node;
import com.hazelcast.internal.cluster.impl.ClusterServiceImpl;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static com.hazelcast.test.HazelcastTestSupport.assertClusterSizeEventually;
import static com.hazelcast.test.HazelcastTestSupport.closeConnectionBetween;
import static com.hazelcast.test.HazelcastTestSupport.getNode;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class LiteMemberJoinTest {

    @Before
    @After
    public void cleanup() {
        HazelcastInstanceManager.terminateAll();
    }

    @Test
    public void test_liteMemberIsCreated() {
        final Config liteConfig = new Config().setLiteMember(true);
        final HazelcastInstance liteInstance = Hazelcast.newHazelcastInstance(liteConfig);

        assertTrue(getNode(liteInstance).isLiteMember());
        final Member liteMember = liteInstance.getCluster().getLocalMember();
        assertTrue(liteMember.isLiteMember());
    }

    @Test
    public void test_liteMemberBecomesMaster_tcp() {
        test_liteMemberBecomesMaster(ConfigCreator.TCP_CONFIG_CREATOR);
    }

    @Test
    public void test_liteMemberBecomesMaster_multicast() {
        test_liteMemberBecomesMaster(ConfigCreator.MULTICAST_CONFIG_CREATOR);
    }

    private void test_liteMemberBecomesMaster(final ConfigCreator configCreator) {
        final HazelcastInstance liteMaster = Hazelcast.newHazelcastInstance(configCreator.create(true));
        final HazelcastInstance other = Hazelcast.newHazelcastInstance(configCreator.create(false));

        assertTrue(getNode(liteMaster).isMaster());
        assertClusterSizeEventually(2, liteMaster);
        assertClusterSizeEventually(2, other);

        final Set<Member> members = other.getCluster().getMembers();
        assertLiteMemberExcluding(members, other);
    }

    @Test
    public void test_liteMemberJoinsToCluster_tcp() {
        test_liteMemberJoinsToCluster(ConfigCreator.TCP_CONFIG_CREATOR);
    }

    @Test
    public void test_liteMemberJoinsToCluster_multicast() {
        test_liteMemberJoinsToCluster(ConfigCreator.MULTICAST_CONFIG_CREATOR);
    }

    private void test_liteMemberJoinsToCluster(final ConfigCreator configCreator) {
        final HazelcastInstance master = Hazelcast.newHazelcastInstance(configCreator.create(false));
        Hazelcast.newHazelcastInstance(configCreator.create(true));

        final Set<Member> members = master.getCluster().getMembers();
        assertLiteMemberExcluding(members, master);
    }

    @Test
    public void test_liteMemberBecomesVisibleTo2ndNode_tcp() {
        test_liteMemberBecomesVisibleTo2ndNode(ConfigCreator.TCP_CONFIG_CREATOR);
    }

    @Test
    public void test_liteMemberBecomesVisibleTo2ndNode_multicast() {
        test_liteMemberBecomesVisibleTo2ndNode(ConfigCreator.MULTICAST_CONFIG_CREATOR);
    }

    private void test_liteMemberBecomesVisibleTo2ndNode(final ConfigCreator configCreator) {
        final HazelcastInstance master = Hazelcast.newHazelcastInstance(configCreator.create(false));
        final HazelcastInstance other = Hazelcast.newHazelcastInstance(configCreator.create(false));
        Hazelcast.newHazelcastInstance(configCreator.create(true));

        assertClusterSizeEventually(3, other);

        final Set<Member> members = other.getCluster().getMembers();
        assertLiteMemberExcluding(members, master, other);
    }

    @Test
    public void test_liteMemberBecomesVisibleTo3rdNode_tcp() {
        test_liteMemberBecomesVisibleTo3rdNode(ConfigCreator.TCP_CONFIG_CREATOR);
    }

    @Test
    public void test_liteMemberBecomesVisibleTo3rdNode_multicast() {
        test_liteMemberBecomesVisibleTo3rdNode(ConfigCreator.MULTICAST_CONFIG_CREATOR);
    }

    private void test_liteMemberBecomesVisibleTo3rdNode(final ConfigCreator configCreator) {
        final HazelcastInstance master = Hazelcast.newHazelcastInstance(configCreator.create(false));
        Hazelcast.newHazelcastInstance(configCreator.create(true));
        final HazelcastInstance other = Hazelcast.newHazelcastInstance(configCreator.create(false));

        final Set<Member> members = other.getCluster().getMembers();
        assertLiteMemberExcluding(members, master, other);
    }

    @Test
    public void test_liteMemberReconnects_tcp() {
        test_liteMemberReconnects(ConfigCreator.TCP_CONFIG_CREATOR);
    }

    @Test
    public void test_liteMemberReconnects_multicast() {
        test_liteMemberReconnects(ConfigCreator.MULTICAST_CONFIG_CREATOR);
    }

    private void test_liteMemberReconnects(final ConfigCreator configCreator) {
        final HazelcastInstance master = Hazelcast.newHazelcastInstance(configCreator.create(false));
        final HazelcastInstance liteInstance = Hazelcast.newHazelcastInstance(configCreator.create(true));

        closeConnectionBetween(master, liteInstance);

        assertClusterSizeEventually(1, master);
        assertClusterSizeEventually(1, liteInstance);

        reconnect(master, liteInstance);

        assertClusterSizeEventually(2, master);
        assertClusterSizeEventually(2, liteInstance);

        final Set<Member> members = master.getCluster().getMembers();
        assertLiteMemberExcluding(members, master);
    }

    private void reconnect(final HazelcastInstance instance1, final HazelcastInstance instance2) {
        final Node node1 = getNode(instance1);
        final Node node2 = getNode(instance2);

        final ClusterServiceImpl clusterService = node1.getClusterService();
        clusterService.merge(node2.address);
    }

    private void assertLiteMemberExcluding(final Set<Member> members, final HazelcastInstance... membersToExclude) {
        final Set<Member> membersCopy = new HashSet<Member>(members);

        assertTrue((members.size() - 1) == membersToExclude.length);

        for (HazelcastInstance memberToExclude : membersToExclude) {
            assertTrue(membersCopy.remove(memberToExclude.getCluster().getLocalMember()));
        }

        final Member liteMember = membersCopy.iterator().next();
        assertTrue(liteMember.isLiteMember());
    }

    private enum ConfigCreator {

        TCP_CONFIG_CREATOR {
            @Override
            public Config create(boolean liteMember) {
                Config config = new Config();
                config.setLiteMember(liteMember);

                NetworkConfig networkConfig = config.getNetworkConfig();
                JoinConfig join = networkConfig.getJoin();
                join.getMulticastConfig().setEnabled(false);
                TcpIpConfig tcpIpConfig = join.getTcpIpConfig();
                tcpIpConfig.setEnabled(true);
                tcpIpConfig.addMember("127.0.0.1");

                return config;
            }
        },

        MULTICAST_CONFIG_CREATOR {
            @Override
            public Config create(boolean liteMember) {
                Config config = new Config();
                config.setLiteMember(liteMember);

                NetworkConfig networkConfig = config.getNetworkConfig();
                JoinConfig join = networkConfig.getJoin();
                join.getTcpIpConfig().setEnabled(false);
                join.getMulticastConfig().setEnabled(true);

                return config;
            }
        };

        public abstract Config create(boolean liteMember);

    }

}
