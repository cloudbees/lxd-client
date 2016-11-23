package com.cloudbees.lxd.client;

import com.cloudbees.lxd.client.api.ContainerAction;
import com.cloudbees.lxd.client.api.ContainerInfo;
import com.cloudbees.lxd.client.api.ImageInfo;
import com.cloudbees.lxd.client.api.LxdResponse;
import com.cloudbees.lxd.client.api.Operation;
import com.cloudbees.lxd.client.api.ServerState;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RxLxdClientTest {

    @Test
    public void serverStateTest() throws Exception {
        try (
            TestHelper t = new TestHelper.Builder().dispatchJsonFile("/1.0", "serverState-trusted.json").build();
            RxLxdClient client = new RxLxdClient(t.getConfig())
        ) {
            ServerState serverState = client.serverState().blockingGet();

            RecordedRequest rr = t.server.takeRequest();
            assertEquals("/1.0", rr.getPath());

            assertEquals("1.0", serverState.getApiVersion());
            assertEquals("trusted", serverState.getAuth());
            assertEquals("lxc", serverState.getEnvironment().getDriver());
        }
    }

    @Test
    public void serverStateFailingTest() throws Exception {
        try (
            TestHelper t = new TestHelper.Builder().dispatchForUrl("/1.0", r -> new MockResponse().setResponseCode(404)).build();
            RxLxdClient client = new RxLxdClient(t.getConfig())
        ) {
            try {
                client.serverState().blockingGet();
                fail();
            } catch (Exception e) {
                Assert.assertTrue(e instanceof LxdClientException);
            }
        }
    }


    @Test
    public void imagesListTest() throws Exception {
        try (
            TestHelper t = new TestHelper.Builder().dispatchJsonFile("/1.0/images?recursion=1", "listImages.json").build();
            RxLxdClient client = new RxLxdClient(t.getConfig())
        ) {
            List<ImageInfo> imageInfoList = client.images().blockingGet();

            assertEquals(1, imageInfoList.size());
            ImageInfo first = imageInfoList.get(0);
            assertEquals("6f25adef061c3f2186c6910bff8cacd0c63e1493e3f8b616e52eb84076890bd1", first.getFingerprint());
            assertEquals("https://cloud-images.ubuntu.com/releases", first.getUpdateSource().getServer());
            assertEquals("ubuntu", first.getAliases().get(0).getName());
        }
    }
}