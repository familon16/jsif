package e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.maxondev.jsif.SelfInitializedFake;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class SelfInitializingFakeE2E {
    private final static int dummyPort = 7088;
    private final static int proxyPort = 7089;
    private final String baseUrl = "http://localhost";
    private final String httpPath = "/some/thing";
    private final String proxyTo = baseUrl + ":" + dummyPort;

    private WireMockServer setUpDummyRemoteServer() {
        WireMockServer dummy = new WireMockServer(dummyPort);
        dummy.stubFor(get(urlEqualTo(httpPath))
                .willReturn(aResponse()
                        .withBody("Hello world!" + UUID.randomUUID().toString())));
        dummy.start();
        return dummy;
    }

    private String doHttpCall(int port) throws IOException {
        Content content = Request.Get(baseUrl + ":" + port + httpPath)
                .execute().returnContent();
        return content.asString();
    }

    @Test
    public void selfInitializedFake() throws IOException {
        String path = "sif_recordings";
        cleanUpRecordings(path);

        SelfInitializedFake fake = SelfInitializedFake
                .builder()
                .proxyTo(proxyTo)
                .recordTo(path)
                .listenOnPort(proxyPort)
                .asAutoMode();

        fake.start(); // no recording yet, so will start in `recorder mode`
        WireMockServer dummy = setUpDummyRemoteServer();

        String resultFromRemote = doHttpCall(proxyPort);

        fake.stop();
        dummy.stop();

        fake.start(); // this time it should start up in `Player` mode

        String recordedResult = doHttpCall(proxyPort);
        fake.stop();

        Assert.assertEquals(resultFromRemote, recordedResult);
    }

    @Test
    public void recordThenPlayback() throws IOException {
        String recordingsPath = "record_then_playback";
        cleanUpRecordings(recordingsPath);

        // Start up recorder
        SelfInitializedFake recorder = SelfInitializedFake
                .builder()
                .proxyTo(proxyTo)
                .recordTo(recordingsPath)
                .listenOnPort(proxyPort)
                .asRecorder();
        recorder.start();

        // set up dummy server
        WireMockServer dummy = setUpDummyRemoteServer();

        // do call proxying via jisf proxy (test -> recorder -> dummy -> recorder -> test)
        String resultFromRemote = doHttpCall(proxyPort);

        // Now we can stop both remote server & recorder proxy
        recorder.stop();
        dummy.stop();

        // Let's load recorded data and start the fake
        SelfInitializedFake fake = SelfInitializedFake
                .builder()
                .proxyTo(proxyTo)
                .recordTo(recordingsPath)
                .listenOnPort(proxyPort)
                .asFakeServer();

        fake.start();

        // This call will go to the recorded fake
        String recordedResult = doHttpCall(proxyPort);

        fake.stop();

        Assert.assertEquals(resultFromRemote, recordedResult);
    }

    private void cleanUpRecordings(String path) {
        FilesUtils.deleteRecursive(new File("src/test/resources/", path));
    }

}