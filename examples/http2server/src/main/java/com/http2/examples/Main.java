package com.http2.examples;

import com.http2.examples.resources.Http2Resource;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

public class Main extends Application<MainConfiguration> {

  public static void main(final String[] args) throws Exception {
    new Main().run(args);
  }

  @Override
  public String getName() {
    return "examples of http2";
  }

  @Override
  public void initialize(final Bootstrap<MainConfiguration> bootstrap) {
    // TODO: application initialization
  }

  @Override
  public void run(MainConfiguration configuration, Environment environment) throws Exception {
      final Http2Resource resource = new Http2Resource();
      environment.jersey().register(resource);
      environment.jersey().register(MultiPartFeature.class);
      environment.getApplicationContext().setMaxFormContentSize(50 * 100000);
  }
}
