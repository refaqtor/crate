/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.execution.engine.collect.collectors;

import com.carrotsearch.hppc.IntArrayList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import io.crate.metadata.SearchPath;
import io.crate.metadata.settings.SessionSettings;
import io.crate.analyze.WhereClause;
import io.crate.breaker.RamAccountingContext;
import io.crate.exceptions.JobKilledException;
import io.crate.execution.dsl.phases.RoutedCollectPhase;
import io.crate.execution.engine.collect.stats.JobsLogs;
import io.crate.execution.jobs.TasksService;
import io.crate.execution.jobs.kill.KillJobsRequest;
import io.crate.execution.jobs.kill.TransportKillJobsNodeAction;
import io.crate.execution.jobs.transport.JobRequest;
import io.crate.execution.jobs.transport.JobResponse;
import io.crate.execution.jobs.transport.TransportJobAction;
import io.crate.metadata.Routing;
import io.crate.metadata.RowGranularity;
import io.crate.planner.distribution.DistributionInfo;
import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.testing.TestingRowConsumer;
import io.crate.types.DataTypes;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.TransportService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.crate.testing.TestingHelpers.createReference;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class RemoteCollectorTest extends CrateDummyClusterServiceUnitTest {

    private TransportJobAction transportJobAction;
    private TransportKillJobsNodeAction transportKillJobsNodeAction;
    private RemoteCollector remoteCollector;
    private TestingRowConsumer consumer;

    @Captor
    public ArgumentCaptor<ActionListener<JobResponse>> listenerCaptor;
    private AtomicInteger numBroadcastCalls;

    @Before
    public void prepare() {
        MockitoAnnotations.initMocks(this);
        UUID jobId = UUID.randomUUID();
        RoutedCollectPhase collectPhase = new RoutedCollectPhase(
            jobId,
            0,
            "remoteCollect",
            new Routing(ImmutableMap.of("remoteNode", ImmutableMap.of("dummyTable", IntArrayList.from(1)))),
            RowGranularity.DOC,
            Collections.singletonList(createReference("name", DataTypes.STRING)),
            Collections.emptyList(),
            WhereClause.MATCH_ALL.queryOrFallback(),
            DistributionInfo.DEFAULT_BROADCAST
        );
        transportJobAction = mock(TransportJobAction.class);
        TasksService tasksService = new TasksService(
            Settings.EMPTY,
            clusterService,
            new JobsLogs(() -> true));
        numBroadcastCalls = new AtomicInteger(0);
        transportKillJobsNodeAction = new TransportKillJobsNodeAction(
            Settings.EMPTY,
            tasksService,
            clusterService,
            mock(TransportService.class)
        ) {
            @Override
            public void broadcast(KillJobsRequest request, ActionListener<Long> listener) {
                numBroadcastCalls.incrementAndGet();
            }
        };
        consumer = new TestingRowConsumer();
        remoteCollector = new RemoteCollector(
            jobId,
            new SessionSettings("dummyUser", SearchPath.createSearchPathFrom("dummySchema")),
            "localNode",
            "remoteNode",
            transportJobAction,
            transportKillJobsNodeAction,
            MoreExecutors.directExecutor(),
            tasksService,
            mock(RamAccountingContext.class),
            consumer,
            collectPhase
        );
    }

    @Test
    public void testKillBeforeContextCreation() throws Exception {
        remoteCollector.kill(new InterruptedException("KILLED"));
        remoteCollector.doCollect();

        verify(transportJobAction, times(0)).execute(eq("remoteNode"), any(JobRequest.class), any(ActionListener.class));

        expectedException.expect(InterruptedException.class);
        consumer.getResult();
    }


    @Test
    public void testRemoteContextIsNotCreatedIfKillHappensBeforeCreateRemoteContext() throws Exception {
        remoteCollector.createLocalContext();
        remoteCollector.kill(new InterruptedException());
        remoteCollector.createRemoteContext();

        verify(transportJobAction, times(0)).execute(eq("remoteNode"), any(JobRequest.class), any(ActionListener.class));
        expectedException.expect(JobKilledException.class);
        consumer.getResult();
    }

    @Test
    public void testKillRequestsAreMadeIfCollectorIsKilledAfterRemoteContextCreation() throws Exception {
        remoteCollector.doCollect();
        verify(transportJobAction, times(1)).execute(eq("remoteNode"), any(JobRequest.class), listenerCaptor.capture());

        remoteCollector.kill(new InterruptedException());

        ActionListener<JobResponse> listener = listenerCaptor.getValue();
        listener.onResponse(new JobResponse());

        assertThat(numBroadcastCalls.get(), is(1));
    }
}
