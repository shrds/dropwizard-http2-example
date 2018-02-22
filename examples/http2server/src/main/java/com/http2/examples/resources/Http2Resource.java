package com.http2.examples.resources;

import com.codahale.metrics.annotation.Timed;
import com.http2.api.PostEntities;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.Boundary;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartMediaTypes;
import org.jvnet.mimepull.MIMEConfig;
import org.jvnet.mimepull.MIMEMessage;
import org.jvnet.mimepull.MIMEPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/** @author Stephen Durfey */
@Path("/http2")
public class Http2Resource {

  private static final Logger LOGGER = LoggerFactory.getLogger(Http2Resource.class);

  public Http2Resource() {}

  @GET
  @Timed
  public String get() {
    return "hello";
  }

  @POST
  @Timed
  @Path("payload")
  @Consumes(MediaType.APPLICATION_JSON)
  public int post(PostEntities body) {
    return body.getEntities().size();
  }

  @POST
  @Timed
  public int post(@Context HttpHeaders headers, InputStream stream) throws IOException {
    String s = IOUtils.toString(new InputStreamReader(stream));
    return s.length();
  }

  @POST
  @Timed
  @Path("multipart")
  @Consumes(MultiPartMediaTypes.MULTIPART_MIXED)
  public long postMultipart(@Context HttpHeaders headers, MultiPart multiPart) throws IOException {
    List<String> requestHeader = headers.getRequestHeader("Content-Encoding");
//    requestHeader.forEach(System.out::println);
    
    int sum = 0;
    for (final BodyPart bodyPart : multiPart.getBodyParts()) {
      // converts the data from the underlying input stream into the
      // requested type. this is pretty handy since it will use the
      // registered message body reader for conversion. if the type
      // isn't known, then the input stream can be retrieved and bytes
      // pulled from there. the input stream should be scoped to
      // the body part, and not the entire stream.
      //
      // e.g. InputStream stream = ((BodyPartEntity)bodyPart.getEntity()).getInputStream();
      //
      // Using MultiPart should be able to replace parsing an InputStream, as
      // seen in the method below. definitely need to evaluate this to check
      // memory pressure. i'm assuming since the bodypart wraps around an
      // input stream, that the entire multipart isn't stored in memory.
      //
      // Funny enough, a BodyPartEntity, is a wrapper around MIMEPart, and MIMEPart
      // is a construct from the mimepull library, which is used in the method
      // below.
      sum += bodyPart.getEntityAs(String.class).length();
    }

    return sum;
  }

  @POST
  @Timed
  @Path("stream")
  @Consumes(MultiPartMediaTypes.MULTIPART_MIXED)
  public long postStream(@Context HttpHeaders headers, InputStream stream) throws IOException {
    String boundary = headers.getMediaType().getParameters().get(Boundary.BOUNDARY_PARAMETER);

    LOGGER.debug("boundary: " + boundary);

    int partIndex = 0;

    MIMEMessage message = new MIMEMessage(stream, boundary, new MIMEConfig());

    boolean hasNext = true;

    long sum = 0;
    while (hasNext) {
      try {
        MIMEPart part = message.getPart(partIndex);

        String s = IOUtils.toString(new InputStreamReader(part.read()));
        sum += Integer.parseInt(s);
        partIndex++;
      } catch (IllegalStateException e) {
        LOGGER.debug("number of elements in stream: " + partIndex);
        hasNext = false;
      }
    }

    return sum;
  }
}
