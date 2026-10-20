package com.argus.feedgateway.adapter.in.binance;

import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;

/**
 * A WebSocket server on a free local port, standing in for Binance: Tomcat's own WebSocket support,
 * already on the gateway's classpath, so tests need no extra dependency.
 */
final class TestWebSocketServer implements AutoCloseable {

  static final String PATH = "/stream";

  private final Tomcat tomcat = new Tomcat();
  private final BlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
  private final BlockingQueue<ByteBuffer> pongs = new LinkedBlockingQueue<>();
  private final Map<String, List<String>> handshakeHeaders =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private volatile String handshakeQuery = "";

  TestWebSocketServer() throws IOException, LifecycleException {
    tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
    tomcat.setPort(0);
    Context context = tomcat.addContext("", null);
    // Requests reach the WebSocket upgrade filter only when some servlet is mapped
    Tomcat.addServlet(context, "default", new HttpServlet() {});
    context.addServletMappingDecoded("/", "default");
    context.addServletContainerInitializer(new WsSci(), null);
    context.addServletContainerInitializer(
        (classes, servletContext) -> {
          ServerContainer container =
              (ServerContainer) servletContext.getAttribute(ServerContainer.class.getName());
          try {
            container.addEndpoint(
                ServerEndpointConfig.Builder.create(ServerSide.class, PATH)
                    .configurator(new Configurator())
                    .build());
          } catch (jakarta.websocket.DeploymentException e) {
            throw new IllegalStateException(e);
          }
        },
        null);
    tomcat.getConnector();
    tomcat.start();
  }

  URI uri() {
    return URI.create("ws://localhost:" + tomcat.getConnector().getLocalPort());
  }

  /** The next client that connects. */
  Session accept() throws InterruptedException {
    Session session = sessions.poll(10, TimeUnit.SECONDS);
    if (session == null) {
      throw new AssertionError("no client connected");
    }
    return session;
  }

  /** The payload of the next pong a client sent. */
  ByteBuffer nextPong() throws InterruptedException {
    ByteBuffer pong = pongs.poll(10, TimeUnit.SECONDS);
    if (pong == null) {
      throw new AssertionError("no pong received");
    }
    return pong;
  }

  Map<String, List<String>> handshakeHeaders() {
    return handshakeHeaders;
  }

  String handshakeQuery() {
    return handshakeQuery;
  }

  @Override
  public void close() throws LifecycleException {
    tomcat.stop();
    tomcat.destroy();
  }

  private final class Configurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(
        ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
      handshakeHeaders.putAll(request.getHeaders());
      handshakeQuery = request.getQueryString();
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) {
      return endpointClass.cast(new ServerSide());
    }
  }

  private final class ServerSide extends Endpoint {

    @Override
    public void onOpen(Session session, EndpointConfig config) {
      session.addMessageHandler(
          new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage pong) {
              pongs.add(pong.getApplicationData());
            }
          });
      sessions.add(session);
    }
  }
}
