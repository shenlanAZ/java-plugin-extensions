/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.plugin.resin.v3;

import com.caucho.server.connection.CauchoRequest;
import com.caucho.server.http.HttpResponse;
import java.util.List;

import io.skywalking.apm.plugin.resin.v3.ResinV3Interceptor;
import org.apache.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;

/**
 * ResinInterceptorTest
 *
 * @author baiyang
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ResinV3InterceptorTest {
    private ResinV3Interceptor interceptor;
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private CauchoRequest request;
    @Mock
    private HttpResponse response;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    private Object[] arguments;
    private Class[] argumentType;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new ResinV3Interceptor();
        when(request.getPageURI()).thenReturn("/test/testRequestURL");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatusCode()).thenReturn(200);
        arguments = new Object[] {request, response};
        argumentType = new Class[] {request.getClass(), response.getClass()};
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
        when(request.getHeader(SW3CarrierItem.HEADER_NAME)).thenReturn("1.333.2345|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");

        interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        assertTraceSegmentRef(traceSegment.getRefs().get(0));
    }

    @Test
    public void testWithOccurException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        interceptor.handleMethodException(enhancedInstance, null, arguments, argumentType, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        MatcherAssert.assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getEntryServiceInstanceId(ref), is(1));
        MatcherAssert.assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.333.2345"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.RESIN);
        SpanAssert.assertTag(span, 0, "http://localhost:8080/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

}
