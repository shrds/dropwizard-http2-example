package com.http2.examples;

import okhttp3.*;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.eclipse.jetty.util.ssl.SslContextFactory.TRUST_ALL_CERTS;

@Ignore
public class Http2ClientTest {

  @Rule public TestName name = new TestName();

  private static int streamStart = 0;
  private static int streamStop = 10000;
  private static int sum;
  private static final String urlRoot = "https://10.190.111.80";

  @BeforeClass
  public static void start() {
    sum = IntStream.range(streamStart, streamStop).sum();
  }

  @Test
  public void test() {
    long start = System.currentTimeMillis();
      Random random = new Random();
      RandomStringGenerator generator =
              new RandomStringGenerator.Builder()
                      .withinRange('a', 'z')
                      .usingRandom(random::nextInt)
                      .build();
      String generate = generator.generate(50000000);
    System.out.println("Length: " + generate.length());
      System.out.println(System.currentTimeMillis() - start);
  }

  @Test
  @Ignore
  public void test_OkHttp_Http1_1()
      throws InterruptedException, IOException, KeyManagementException, NoSuchAlgorithmException {
    // Install the all-trusting trust manager
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
    // Create an ssl socket factory with our all-trusting manager
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

    // create a client to communicate over HTTP 1.1
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .protocols(Arrays.asList(Protocol.HTTP_1_1))
            .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    List<RequestBody> bodies =
        IntStream.range(streamStart, streamStop)
            .mapToObj(i -> RequestBody.create(MediaType.parse("text/plain"), String.valueOf(i)))
            .collect(Collectors.toList());

    String url = urlRoot + ":8443/http2";
    long start = System.currentTimeMillis();
    final CountDownLatch latch = new CountDownLatch(bodies.size());
    for (final RequestBody body : bodies) {
      Request request = new Request.Builder().url(url).post(body).build();
      client
          .newCall(request)
          .enqueue(
              new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                  e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                  assert response.protocol() == Protocol.HTTP_1_1;
                  latch.countDown();
                }
              });
    }

    latch.await();
    long stop = System.currentTimeMillis();

    System.out.println(
        name.getMethodName()
            + " took "
            + (stop - start)
            + "ms to upload "
            + bodies.size()
            + " entities");
  }

  @Test
  public void test_OkHttp_Http2()
      throws InterruptedException, IOException, KeyManagementException, NoSuchAlgorithmException {
    // Install the all-trusting trust manager
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
    // Create an ssl socket factory with our all-trusting manager
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

    // create a client to communicate over HTTP/2
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    List<RequestBody> bodies =
        IntStream.range(streamStart, streamStop)
            .mapToObj(i -> RequestBody.create(MediaType.parse("text/plain"), String.valueOf(i)))
            .collect(Collectors.toList());

    // on the jetty server, port 8445 has http2 enabled
    String url = urlRoot + ":8445/http2";
    long start = System.currentTimeMillis();
    final CountDownLatch latch = new CountDownLatch(bodies.size());
    for (final RequestBody body : bodies) {
      Request request = new Request.Builder().url(url).post(body).build();
      client
          .newCall(request)
          .enqueue(
              new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                  e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                  assert response.protocol() == Protocol.HTTP_2;
                  //                  assert sum == Integer.parseInt(response.body().string());
                  latch.countDown();
                }
              });
    }

    latch.await();
    long stop = System.currentTimeMillis();
    System.out.println(
        name.getMethodName()
            + " took "
            + (stop - start)
            + "ms to upload "
            + bodies.size()
            + " entities");
  }

  @Test
  public void test_OkHttp_Http2_MIMEStream()
      throws InterruptedException, IOException, KeyManagementException, NoSuchAlgorithmException {
    // Install the all-trusting trust manager
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
    // Create an ssl socket factory with our all-trusting manager
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

    // create a client to communicate over HTTP/2
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.MIXED);

    IntStream.range(streamStart, streamStop)
        .mapToObj(i -> RequestBody.create(MediaType.parse("text/plain"), String.valueOf(i)))
        .forEach(body -> builder.addPart(body));

    MultipartBody multipartBody = builder.build();

    String url = urlRoot + ":8445/http2/multipart";
    final CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();
    Request request = new Request.Builder().url(url).post(multipartBody).build();
    client
        .newCall(request)
        .enqueue(
            new okhttp3.Callback() {

              @Override
              public void onFailure(Call call, IOException e) {
                e.printStackTrace();
              }

              @Override
              public void onResponse(Call call, Response response) throws IOException {
                assert response.protocol() == Protocol.HTTP_2;
                //                assert sum == Integer.parseInt(response.body().string());
                latch.countDown();
              }
            });

    latch.await();
    long stop = System.currentTimeMillis();
    System.out.println(
        name.getMethodName()
            + " took "
            + (stop - start)
            + "ms to upload "
            + (streamStop - streamStart)
            + " entities");
  }

  @Test
  public void test_OkHttp_Http1_1_Stream()
      throws InterruptedException, IOException, KeyManagementException, NoSuchAlgorithmException {
    // Install the all-trusting trust manager
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
    // Create an ssl socket factory with our all-trusting manager
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

    // create a client to communicate over HTTP 1.1
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .protocols(Arrays.asList(Protocol.HTTP_1_1))
            .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.MIXED);

    IntStream.range(streamStart, streamStop)
        .mapToObj(i -> RequestBody.create(MediaType.parse("text/plain"), String.valueOf(i)))
        .forEach(body -> builder.addPart(body));

    MultipartBody multipartBody = builder.build();

    // on the jetty server, port 8443 uses http 1.1
    String url = urlRoot + ":8443/http2/stream";
    final CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();
    Request request = new Request.Builder().url(url).post(multipartBody).build();
    client
        .newCall(request)
        .enqueue(
            new okhttp3.Callback() {

              @Override
              public void onFailure(Call call, IOException e) {
                e.printStackTrace();
              }

              @Override
              public void onResponse(Call call, Response response) throws IOException {
                assert response.protocol() == Protocol.HTTP_1_1;
                //                assert sum == Integer.parseInt(response.body().string());
                latch.countDown();
              }
            });

    latch.await();
    long stop = System.currentTimeMillis();
    System.out.println(
        name.getMethodName()
            + " took "
            + (stop - start)
            + "ms to upload "
            + (streamStop - streamStart)
            + " entities");
  }
}
