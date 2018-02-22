package com.http.benchmark;

import com.codahale.metrics.Timer;
import io.airlift.airline.Command;
import okhttp3.*;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** @author Stephen Durfey */
@Command(
  name = "stream",
  description = "Streams a number of entities to a rest end point as MIME multipart/mixed"
)
public class StreamMessageBenchmark extends AbstractBenchmark {

  private static final String ROOT_METRIC_NAME = "http_stream_message";
  private static final String FULL_RESULT = "_full_result";

   @Override
    public Action getAction(List<Protocol> protocols, String url, int numEntities,int payloadSize, String payloadPath) {
        String metricName = ROOT_METRIC_NAME + "_" + numEntities;
        return new Action(
                this::testMessageStream, protocols, url, metricName, numEntities, payloadSize, payloadPath);
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

  private void testMessageStream(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize, String payloadPath)
      throws KeyManagementException, NoSuchAlgorithmException, IOException {

    System.out.println("Starting upload testing for " + metricName);
    if(StringUtils.isEmpty(payloadPath)){
        System.out.println("Please provide a valid path of the file to be upload");
        return;
    }
    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.MIXED);

      InputStream inputStream = null;
      try {
          inputStream = new FileInputStream(new File(payloadPath));
      } catch (FileNotFoundException e) {
          e.printStackTrace();
      }

      InputStream finalInputStream = inputStream;

      IntStream.range(0, numEntities)
        .mapToObj(
            i -> RequestBodyUtil.create(MediaType.parse("application/octet-stream"), finalInputStream))
        .forEach(body -> builder.addPart(body));

    MultipartBody multipartBody = builder.build();

    OkHttpClient client = getHttpClient(protocols, url, metricName);
    Timer.Context timer = registry.timer(metricName + FULL_RESULT).time();
    long start = System.currentTimeMillis();
    try {
      Request.Builder request = new Request.Builder().url(url).post(multipartBody);

      // https://github.com/square/okhttp/issues/3442
      client.dispatcher().setMaxRequestsPerHost(1);
      Response execute = client.newCall(request.build()).execute();

      if (!execute.isSuccessful() || verbose) {
        System.out.println("Received status code: [" + execute.code() + "] with message [" + execute.body().string());
      }
      
      execute.close();
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

  private static class RequestBodyUtil {

        public static RequestBody create(final MediaType mediaType, final InputStream inputStream) {
            return new RequestBody() {
                @Override
                public MediaType contentType() {
                    return mediaType;
                }

                @Override
                public long contentLength() {
                    try {
                        return inputStream.available();
                    } catch (IOException e) {
                        return 0;
                    }
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    Source source = null;
                    try {
                        source = Okio.source(inputStream);
                        sink.writeAll(source);
                    } finally {
                        Util.closeQuietly(source);
                    }
                }
            };
        }
    }
}
