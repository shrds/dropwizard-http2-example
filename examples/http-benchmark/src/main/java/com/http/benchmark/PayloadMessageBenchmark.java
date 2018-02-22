package com.http.benchmark;

import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.http2.api.Entity;
import com.http2.api.PostEntities;
import io.airlift.airline.Command;
import okhttp3.*;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** @author Stephen Durfey */
@Command(
  name = "payload",
  description =
      "Tests writing to an HTTP endpoint writing a payload at a time with a number of entities"
)
public class PayloadMessageBenchmark extends AbstractBenchmark {

  private static final String ROOT_METRIC_NAME = "http_payload_message";
  private static final String FULL_RESULT = "_full_result";


  @Override
  public Action getAction(List<Protocol> protocols, String url, int numEntities, int payloadSize, String payloadPath) {
    String metricName = ROOT_METRIC_NAME + "_" + numEntities;
    return new Action(this::testLargePost, protocols, url, metricName, numEntities, payloadSize, payloadPath);
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

  private void testLargePost(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize, String payloadPath)
      throws KeyManagementException, NoSuchAlgorithmException, IOException {
    OkHttpClient client = getHttpClient(protocols, url, metricName);
    System.out.println("Starting upload testing for " + metricName);

    ObjectMapper mapper = new ObjectMapper();

    List<Entity> entities =
        IntStream.range(0, numEntities)
            .mapToObj(
                i -> {
                  Entity entity = new Entity();
                  entity.setName(generator.generate(payloadSize));
                  entity.setAddress(UUID.randomUUID().toString());
                  return entity;
                })
            .collect(Collectors.toList());

    PostEntities post = new PostEntities();
    post.setEntities(entities);

    RequestBody body =
        RequestBody.create(MediaType.parse("application/json"), mapper.writeValueAsString(post));
    Request.Builder request = new Request.Builder().url(url).post(body);

//    if (BEARER_TOKEN != null) request.addHeader(HttpHeader.AUTHORIZATION.name(), BEARER_TOKEN);

    Timer.Context timer = registry.timer(metricName + FULL_RESULT).time();
    try {
      Response execute = client.newCall(request.build()).execute();
      execute.body().close();
    } finally {
      long stop = timer.stop();
      System.out.println(
          "Total run time for [" + metricName + "] is " + convertToMillis(stop) + "ms");
      client.connectionPool().evictAll();
      if (client.cache() != null) client.cache().close();
    }
  }
}
