package com.hazelcast.internal.monitors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category(QuickTest.class)
public class MemberHazelcastInstanceInfoPluginTest extends AbstractPerformanceMonitorPluginTest {

    private MemberHazelcastInstanceInfoPlugin plugin;

    @Before
    public void setup() {
        HazelcastInstance hz = createHazelcastInstance();
        plugin = new MemberHazelcastInstanceInfoPlugin(getNodeEngineImpl(hz));
        plugin.onStart();
    }

    @Test
    public void testGetPeriodMillis() {
        assertEquals(TimeUnit.SECONDS.toMillis(60), plugin.getPeriodMillis());
    }

    @Test
    public void testRun() throws IOException {
        logWriter.write(plugin);
        assertContains("HazelcastInstance[");
        assertContains("isRunning=true");
        assertContains("Members[");
    }
}