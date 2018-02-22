package com.http.benchmark;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.http2.api.Entity;
import com.http2.api.PostEntities;
import com.opencsv.CSVWriter;
import io.airlift.airline.Command;
import io.airlift.airline.HelpOption;
import io.airlift.airline.Option;
import io.airlift.airline.SingleCommand;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.eclipse.jetty.util.ssl.SslContextFactory.TRUST_ALL_CERTS;

/** @author Stephen Durfey */
@Command(name = "benchmark", description = "tests http/1.1 and http/2 interactions")
public class BenchmarkMain {

  @Inject public HelpOption helpOption;

  @Option(
    name = {"-h1Url", "--http1Url"},
    description = "url to hit for http/1.1 tests"
  )
  public String http1Url;

  @Option(
    name = {"-h2Url", "--http2Url"},
    description = "url to hit for http/2 tests"
  )
  public String http2Url;

  @Option(
    name = {"-o", "--output"},
    description = "file to write results into"
  )
  public String resultsOutputDir;

  @Option(
    name = {"-s", "--single"},
    description = "flags command to upload single messages per request"
  )
  public boolean singleMessageUpload;

  @Option(
    name = {"-c", "--stream"},
    description = "flags command to stream messages per request"
  )
  public boolean streamMessageUpload;

  @Option(
    name = {"-p", "--payload"},
    description = "flags command to upload messages into large payloads per request"
  )
  public boolean largePayloadUpload;

  @Option(
    name = {"-r", "--repetitions"},
    description = "tells command how many times to repeat the benchmark"
  )
  public int repetitions;

  @Option(
    name = {"-t", "--threads"},
    description = "number of threads to execute in parallel"
  )
  public int threads;

  @Option(
    name = {"-v", "--size"},
    description = "size of each entity during the upload"
  )
  public int payloadSize;

  @Option(
    name = {"-e", "--entities"},
    description =
        "number of entities to upload; default uploads in the following increments: 1, 10, 100, 1000, 5000"
  )
  public int numEntities;

  private List<Integer> entityCounts = Arrays.asList(1, 10, 100, 1000, 5000);

  private static final String HTTP1_SINGLE_IDENTIFIER = "http1_single_message";
  private static final String HTTP2_SINGLE_IDENTIFIER = "http2_single_message";
  private static final String HTTP1_STREAM_IDENTIFIER = "http1_stream_message";
  private static final String HTTP2_STREAM_IDENTIFIER = "http2_stream_message";
  private static final String HTTP1_PAYLOAD_IDENTIFIER = "http1_payload_message";
  private static final String HTTP2_PAYLOAD_IDENTIFIER = "http2_payload_message";

  Random random = new Random();
  RandomStringGenerator generator =
      new RandomStringGenerator.Builder()
          .withinRange('a', 'z')
          .usingRandom(random::nextInt)
          .build();

  private MetricRegistry registry = new MetricRegistry();

  private static final List<Protocol> HTTP1 = Arrays.asList(Protocol.HTTP_1_1);
  private static final List<Protocol> HTTP2 = Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1);

  public static void main(String... args)
          throws NoSuchAlgorithmException, KeyManagementException, InterruptedException, IOException, ExecutionException {
    BenchmarkMain cmd = SingleCommand.singleCommand(BenchmarkMain.class).parse(args);

    if (cmd.helpOption.showHelpIfRequested()) {
      return;
    }

    cmd.run();
  }

  public static class Action implements Runnable {

    private final ActionOperator action;
    private final List<Protocol> protocols;
    private final String url;
    private final String metricName;
    private final int numEntities;
    private final int payloadSize;

    public Action(
        ActionOperator action,
        List<Protocol> protocols,
        String url,
        String metricName,
        int numEntities,
        int payloadSize) {
      this.action = action;
      this.protocols = protocols;
      this.url = url;
      this.metricName = metricName;
      this.numEntities = numEntities;
      this.payloadSize = payloadSize;
    }

    @Override
    public void run() {
      try {
        action.run(protocols, url, metricName, numEntities, payloadSize);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public interface ActionOperator {
    void run(
        List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize)
        throws Exception;
  }

  public void run()
          throws KeyManagementException, NoSuchAlgorithmException, InterruptedException, IOException, ExecutionException {

    ExecutorService threadPool = Executors.newFixedThreadPool(threads);

    String streamMessageUrlHttp1 = http1Url + "/multipart";
    String streamMessageUrlHttp2 = http2Url + "/multipart";

    String postMessageUrlHttp1 = http1Url + "/payload";
    String postMessageUrlHttp2 = http2Url + "/payload";

    // if numEntities was set at the command line, override the default values
    if (numEntities > 0) entityCounts = Arrays.asList(numEntities);
    int messageSize = payloadSize > 0 ? payloadSize : 1500;

    System.out.println("Running a benchmark with [" + threads + "] threads and [" + repetitions + "] repetitions. The " +
            "benchmark will upload [" + numEntities + "] entities per action, with a message size of [" + messageSize + "] bytes. " +
            "The following types will be performed: \n singleMessageUpload=[" + singleMessageUpload + "]\n" +
            " streamMessageUpload=[" + streamMessageUpload + "]\n largePayloadUpload=[" + largePayloadUpload + "]\n It will" +
            " hit the following urls: \n http1=[" + http1Url + "]\n http2=[" + http2Url + "]");

    List<Future> futures = new ArrayList<>();
    IntStream.range(0, repetitions)
        .forEach(
            i ->
                entityCounts
                    .stream()
                    .forEach(
                        entities -> {
                          try {
                            String suffix = String.valueOf(entities);

                            if (StringUtils.isNotEmpty(http1Url)) {
                              if (singleMessageUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testIndividualMessages,
                                            HTTP1,
                                            http1Url,
                                            HTTP1_SINGLE_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                              if (streamMessageUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testMessageStream,
                                            HTTP1,
                                            streamMessageUrlHttp1,
                                            HTTP1_STREAM_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                              if (largePayloadUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testMessageStream,
                                            HTTP1,
                                            postMessageUrlHttp1,
                                            HTTP1_PAYLOAD_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                            } else if (StringUtils.isNotEmpty(http2Url)) {

                              if (singleMessageUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testIndividualMessages,
                                            HTTP2,
                                            http2Url,
                                            HTTP2_SINGLE_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                              if (streamMessageUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testMessageStream,
                                            HTTP2,
                                            streamMessageUrlHttp2,
                                            HTTP2_STREAM_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                              if (largePayloadUpload)
                                futures.add(
                                    threadPool.submit(
                                        new Action(
                                            this::testLargePost,
                                            HTTP2,
                                            postMessageUrlHttp2,
                                            HTTP2_PAYLOAD_IDENTIFIER + "_" + suffix,
                                            entities,
                                            messageSize)));

                            } else {
                              System.out.println("No url's specified; nothing to do");
                            }
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                        }));

    System.out.println("number of futures: " + futures.size());
    // wait for all futures to finish before moving out to print the results
    for (final Future future : futures) {
      System.out.println("waiting for future to finish");
      future.get();
    }
    System.out.println("futures have finished");
    printResults(entityCounts);
    threadPool.shutdown();
  }

  private void printResults(List<Integer> entityCounts) throws IOException {
    // create CSV writer
    try (CSVWriter writer = new CSVWriter(new FileWriter(resultsOutputDir))) {
      entityCounts
          .stream()
          .forEach(
              entities -> {
                List<String> identifiers = new ArrayList<>();

                if (StringUtils.isNotEmpty(http1Url)) {
                  if (largePayloadUpload) identifiers.add(HTTP1_SINGLE_IDENTIFIER);
                  if (streamMessageUpload) identifiers.add(HTTP1_STREAM_IDENTIFIER);
                  if (singleMessageUpload) identifiers.add(HTTP1_PAYLOAD_IDENTIFIER);
                } else if (StringUtils.isNotEmpty(http2Url)) {
                  if (largePayloadUpload) identifiers.add(HTTP2_PAYLOAD_IDENTIFIER);
                  if (streamMessageUpload) identifiers.add(HTTP2_STREAM_IDENTIFIER);
                  if (singleMessageUpload) identifiers.add(HTTP2_SINGLE_IDENTIFIER);
                }

                identifiers
                    .stream()
                    .forEach(
                        id -> {
                          String metricName = id + "_" + entities;
                          Timer timer = registry.timer(metricName);
                          Snapshot snapshot = timer.getSnapshot();
                          String line =
                              new StringBuilder()
                                  .append(metricName)
                                  .append(",")
                                  .append(timer.getCount())
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMedian()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMean()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMin()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMax()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get75thPercentile()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get95thPercentile()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get99thPercentile()))
                                  .toString();

                          writer.writeNext(line.split(","));

                          metricName = id + "_" + entities + "_full_request";
                          timer = registry.timer(metricName);
                          snapshot = timer.getSnapshot();
                          line =
                              new StringBuilder()
                                  .append(metricName)
                                  .append(",")
                                  .append(timer.getCount())
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMedian()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMean()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMin()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.getMax()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get75thPercentile()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get95thPercentile()))
                                  .append(",")
                                  .append(convertToMillis(snapshot.get99thPercentile()))
                                  .toString();

                          writer.writeNext(line.split(","));
                        });
              });
    }
  }

  private long convertToMillis(double num) {
    return TimeUnit.MILLISECONDS.convert(Math.round(num), TimeUnit.NANOSECONDS);
  }

  private long convertToMillis(long num) {
    return TimeUnit.MILLISECONDS.convert(Math.round(num), TimeUnit.NANOSECONDS);
  }

  private void testLargePost(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize)
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
    Request request = new Request.Builder().url(url).post(body).build();

    Timer.Context timer = registry.timer(metricName + "_full_request").time();
    try {
      Response execute = client.newCall(request).execute();
      execute.body().close();
    } finally {
      long stop = timer.stop();
      System.out.println(
          "Total run time for [" + metricName + "] is " + convertToMillis(stop) + "ms");
      client.connectionPool().evictAll();
      if (client.cache() != null) client.cache().close();
    }
  }

  private void testIndividualMessages(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize)
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

    Timer.Context timer = registry.timer(metricName + "_full_request").time();
    long start = System.currentTimeMillis();
    int cntr = 0;
    try {
      final CountDownLatch latch = new CountDownLatch(bodies.size());
      for (final RequestBody body : bodies) {
        Request request = new Request.Builder().url(url).post(body).build();

        // https://github.com/square/okhttp/issues/3442
        if (cntr == 0) {
          client.dispatcher().setMaxRequestsPerHost(1);
          Response execute = client.newCall(request).execute();
          execute.close();
          latch.countDown();
          cntr++;
        } else {
          // increases the number of requests on the wire at a time.
          // http/2 uses one connection, so, don't limit to just one message
          // at a time. with http/1.x, this setting will cause it to open
          // 250 TCP connections .... we don't want that.
          if (cntr == 1 && protocols.contains(Protocol.HTTP_2))
            client.dispatcher().setMaxRequestsPerHost(50);

          HttpCallback httpCallback = new HttpCallback(latch);
          client.newCall(request).enqueue(httpCallback);
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

  private void testMessageStream(
      List<Protocol> protocols, String url, String metricName, int numEntities, int payloadSize)
      throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, IOException {

    System.out.println("Starting upload testing for " + metricName);
    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.MIXED);

    IntStream.range(0, numEntities)
        .mapToObj(
            i -> RequestBody.create(MediaType.parse("text/plain"), generator.generate(payloadSize)))
        .forEach(body -> builder.addPart(body));

    MultipartBody multipartBody = builder.build();

    OkHttpClient client = getHttpClient(protocols, url, metricName);
    Timer.Context timer = registry.timer(metricName + "_full_request").time();
    long start = System.currentTimeMillis();
    try {
      Request request = new Request.Builder().url(url).post(multipartBody).build();

      // https://github.com/square/okhttp/issues/3442
      client.dispatcher().setMaxRequestsPerHost(1);
      Response execute = client.newCall(request).execute();
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

  private OkHttpClient getHttpClient(List<Protocol> protocols, String url, String metricName)
      throws NoSuchAlgorithmException, KeyManagementException {

    // create a client to communicate over HTTP/2
    OkHttpClient.Builder builder =
        new OkHttpClient()
            .newBuilder()
            .protocols(protocols)
            //            .connectionPool(new ConnectionPool(100, 5000, TimeUnit.MILLISECONDS))
            .addNetworkInterceptor(new HttpInterceptor(registry.timer(metricName)))
            .hostnameVerifier((hostname, session) -> true);

    if (enableSSL(url)) {
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
      // Create an ssl socket factory with our all-trusting manager
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0]);
    }

    return builder.build();
  }

  private boolean enableSSL(String url) {
    return url.contains("https");
  }

  private class HttpInterceptor implements Interceptor {

    private final Timer timer;

    public HttpInterceptor(Timer timer) {
      this.timer = timer;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      Timer.Context time = timer.time();
      try {
        return chain.proceed(request);
      } finally {
        time.stop();
      }
    }
  }

  private class HttpCallback implements Callback {

    private final CountDownLatch latch;

    public HttpCallback(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onFailure(Call call, IOException e) {
      e.printStackTrace();
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
      latch.countDown();
      response.body().close();
    }
  }
}
