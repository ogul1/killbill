/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.usage.timeline.codec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.usage.timeline.chunks.TimelineChunk;
import com.ning.billing.usage.timeline.chunks.TimelineChunksViews.Compact;
import com.ning.billing.usage.timeline.chunks.TimelineChunksViews.Loose;
import com.ning.billing.usage.timeline.samples.SampleOpcode;
import com.ning.billing.usage.timeline.samples.ScalarSample;
import com.ning.billing.usage.timeline.times.DefaultTimelineCoder;
import com.ning.billing.usage.timeline.times.TimelineCoder;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

public class TestTimelineChunkToJson {

    private static final ObjectMapper mapper = new ObjectMapper().configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    private static final long CHUNK_ID = 1242L;
    private static final int HOST_ID = 1422;
    private static final int SAMPLE_KIND_ID = 1224;
    private static final int SAMPLE_COUNT = 2142;
    private static final DateTime END_TIME = new DateTime(DateTimeZone.UTC);
    private static final DateTime START_TIME = END_TIME.minusMinutes(SAMPLE_COUNT);

    private byte[] samples;
    private TimelineChunk chunk;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final List<DateTime> dateTimes = new ArrayList<DateTime>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream output = new DataOutputStream(out);
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            sampleCoder.encodeSample(output, new ScalarSample<Long>(SampleOpcode.LONG, 10L));
            dateTimes.add(START_TIME.plusMinutes(i));
        }
        output.flush();
        output.close();
        samples = out.toByteArray();

        final DateTime endTime = dateTimes.get(dateTimes.size() - 1);
        final byte[] timeBytes = timelineCoder.compressDateTimes(dateTimes);
        chunk = new TimelineChunk(CHUNK_ID, HOST_ID, SAMPLE_KIND_ID, START_TIME, endTime, timeBytes, samples, SAMPLE_COUNT);
    }

    @Test(groups = "fast")
    public void testTimelineChunkCompactMapping() throws Exception {
        final String chunkToString = mapper.writerWithView(Compact.class).writeValueAsString(chunk);
        final Map chunkFromString = mapper.readValue(chunkToString, Map.class);
        Assert.assertEquals(chunkFromString.keySet().size(), 11);
        Assert.assertEquals(chunkFromString.get("hostId"), HOST_ID);
        Assert.assertEquals(chunkFromString.get("sampleKindId"), SAMPLE_KIND_ID);
        Assert.assertEquals(new TextNode((String) chunkFromString.get("samples")).binaryValue(), samples);
        Assert.assertEquals(chunkFromString.get("sampleCount"), SAMPLE_COUNT);
        Assert.assertEquals(chunkFromString.get("startTime"), START_TIME.getMillis());
        Assert.assertEquals(chunkFromString.get("aggregationLevel"), 0);
        Assert.assertEquals(chunkFromString.get("notValid"), false);
        Assert.assertEquals(chunkFromString.get("dontAggregate"), false);
    }

    @Test(groups = "fast")
    public void testTimelineChunkLooseMapping() throws Exception {
        final String chunkToString = mapper.writerWithView(Loose.class).writeValueAsString(chunk);
        final Map chunkFromString = mapper.readValue(chunkToString, Map.class);
        Assert.assertEquals(chunkFromString.keySet().size(), 4);
        Assert.assertEquals(chunkFromString.get("hostId"), HOST_ID);
        Assert.assertEquals(chunkFromString.get("sampleKindId"), SAMPLE_KIND_ID);
        Assert.assertNotNull(chunkFromString.get("samplesAsCSV"));
    }
}
