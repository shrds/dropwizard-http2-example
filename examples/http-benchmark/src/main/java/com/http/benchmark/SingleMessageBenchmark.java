package com.http.benchmark;

import com.codahale.metrics.Timer;
import io.airlift.airline.Command;
import okhttp3.*;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** @author Stephen Durfey */
@Command(
  name = "single",
  description = "Tests writing to an HTTP endpoint writing a single message per request"
)
public class SingleMessageBenchmark extends AbstractBenchmark {

  private static final String ROOT_METRIC_NAME = "http_single_message";
  private static final String FULL_RESULT = "_full_result";

  @Override
  public Action getAction(List<Protocol> protocols, String url, int numEntities, int payloadSize, String payloadPath) {
    String metricName = ROOT_METRIC_NAME + "_" + numEntities;
    return new Action(
        this::testIndividualMessages, protocols, url, metricName, numEntities, payloadSize, payloadPath);
  }

  @Override
  public Map<String, Timer> getTimers(int numEntities) {
    Map<String, Timer> timers = new HashMap<>();
    String streamName = ROOT_METRIC_NAME + "_" + numEntities;
    timers.put(streamName, registry.timer(streamName));
    String fullName = streamName + FULL_RESULT;
    timers.put(fullName, registry.timer(streamName));
    return timers;
  }

  private void testIndividualMessages(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize, String payloadPath)
      throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, IOException {

    OkHttpClient client = getHttpClient(protocols, url, metricName);
    System.out.println("Starting upload testing for " + metricName);

    List<RequestBody> bodies =
        IntStream.range(0, numEntities)
            .mapToObj(
                i ->
                    RequestBody.create(
                        MediaType.parse("text/plain"), generator.generate(payloadSize)))
            .collect(Collectors.toList());

    Timer.Context timer = registry.timer(metricName + FULL_RESULT).time();
    long start = System.currentTimeMillis();
    int cntr = 0;
    try {
      final CountDownLatch latch = new CountDownLatch(bodies.size());
      for (final RequestBody body : bodies) {
        Request.Builder request = new Request.Builder().url(url).post(body);

//        if (BEARER_TOKEN != null) request.addHeader(HttpHeader.AUTHORIZATION.name(), BEARER_TOKEN);

        // https://github.com/square/okhttp/issues/3442
        if (cntr == 0) {
          client.dispatcher().setMaxRequestsPerHost(1);
          Response execute = client.newCall(request.build()).execute();
          execute.close();
          latch.countDown();
          cntr++;
        } else {
          // increases the number of requests on the wire at a time.
          // http/2 uses one connection, so, don't limit to just one message
          // at a time. with http/1.x, this setting will cause it to open
          // n TCP connections .... we don't want that.
          if (cntr == 1 && protocols.contains(Protocol.HTTP_2))
            client.dispatcher().setMaxRequestsPerHost(50);

          HttpCallback httpCallback = new HttpCallback(latch);
          client.newCall(request.build()).enqueue(httpCallback);
          cntr++;
        }
      }
      latch.await();
    } finally {
      timer.stop();
      System.out.println(
          "Total run time for ["
              + metricName
              + "] is "
              + (System.currentTimeMillis() - start)
              + "ms");
      client.connectionPool().evictAll();
      if (client.cache() != null) client.cache().close();
    }
  }
}
