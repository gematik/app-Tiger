/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.scheduler;

import static de.gematik.test.tiger.mockserver.mock.HttpState.getPort;
import static de.gematik.test.tiger.mockserver.mock.HttpState.setPort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.SocketCommunicationException;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpForwardActionResult;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class Scheduler {

  private final MockServerConfiguration configuration;
  private final ScheduledExecutorService scheduler;

  private final boolean synchronous;

  public static class SchedulerThreadFactory implements ThreadFactory {

    private final String name;
    private final boolean daemon;
    private static int threadInitNumber;

    public SchedulerThreadFactory(String name) {
      this.name = name;
      this.daemon = true;
    }

    public SchedulerThreadFactory(String name, boolean daemon) {
      this.name = name;
      this.daemon = daemon;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "MockServer-" + name + threadInitNumber++);
      thread.setDaemon(daemon);
      return thread;
    }
  }

  public Scheduler(MockServerConfiguration configuration) {
    this(configuration, false);
  }

  @VisibleForTesting
  public Scheduler(MockServerConfiguration configuration, boolean synchronous) {
    this.configuration = configuration;
    this.synchronous = synchronous;
    if (!this.synchronous) {
      this.scheduler =
          new ScheduledThreadPoolExecutor(
              configuration.actionHandlerThreadCount(),
              new SchedulerThreadFactory("Scheduler"),
              new ThreadPoolExecutor.CallerRunsPolicy());
    } else {
      this.scheduler = null;
    }
  }

  public synchronized void shutdown() {
    if (!scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        scheduler.awaitTermination(500, MILLISECONDS);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void run(Runnable command, Integer port) {
    setPort(port);
    try {
      command.run();
    } catch (RuntimeException throwable) {
      log.info("Error", throwable);
    }
  }

  public void schedule(Runnable command, boolean synchronous) {
    Integer port = getPort();
    run(command, port);
  }

  public void submit(Runnable command) {
    submit(command, false);
  }

  public void submit(Runnable command, boolean synchronous) {
    Integer port = getPort();
    if (this.synchronous || synchronous) {
      run(command, port);
    } else {
      scheduler.submit(() -> run(command, port));
    }
  }

  public void submit(
      HttpForwardActionResult future,
      Runnable command,
      boolean synchronous,
      Predicate<Throwable> logException) {
    Integer port = getPort();
    if (future != null) {
      if (this.synchronous || synchronous) {
        try {
          future.getHttpResponse().get(configuration.maxSocketTimeoutInMillis(), MILLISECONDS);
        } catch (TimeoutException e) {
          future
              .getHttpResponse()
              .completeExceptionally(
                  new SocketCommunicationException(
                      "Response was not received after "
                          + configuration.maxSocketTimeoutInMillis()
                          + " milliseconds, to make the proxy wait longer please use"
                          + " \"mockserver.maxSocketTimeout\" system property or"
                          + " configuration.maxSocketTimeout(long milliseconds)",
                      e.getCause()));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          future.getHttpResponse().completeExceptionally(ex);
        } catch (ExecutionException ex) {
          future.getHttpResponse().completeExceptionally(ex);
        }
        run(command, port);
      } else {
        future
            .getHttpResponse()
            .whenCompleteAsync(
                (httpResponse, throwable) -> {
                  if (throwable != null && logException.test(throwable)) {
                    log.info(throwable.getMessage(), throwable);
                  }
                  run(command, port);
                },
                scheduler);
      }
    }
  }

  public void submit(
      CompletableFuture<BinaryMessage> future, Runnable command, boolean synchronous) {
    Integer port = getPort();
    if (future != null) {
      if (this.synchronous || synchronous) {
        try {
          future.get(configuration.maxSocketTimeoutInMillis(), MILLISECONDS);
        } catch (TimeoutException e) {
          future.completeExceptionally(
              new SocketCommunicationException(
                  "Response was not received after "
                      + configuration.maxSocketTimeoutInMillis()
                      + " milliseconds, to make the proxy wait longer please use"
                      + " \"mockserver.maxSocketTimeout\" system property or"
                      + " ConfigurationProperties.maxSocketTimeout(long milliseconds)",
                  e.getCause()));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          future.completeExceptionally(ex);
        } catch (ExecutionException ex) {
          future.completeExceptionally(ex);
        }
        run(command, port);
      } else {
        future.whenCompleteAsync((httpResponse, throwable) -> command.run(), scheduler);
      }
    }
  }

  public void submit(
      HttpForwardActionResult future,
      BiConsumer<HttpResponse, Throwable> consumer,
      boolean synchronous) {
    if (future != null) {
      if (this.synchronous || synchronous) {
        HttpResponse httpResponse = null;
        Throwable exception = null;
        try {
          httpResponse =
              future.getHttpResponse().get(configuration.maxSocketTimeoutInMillis(), MILLISECONDS);
        } catch (TimeoutException e) {
          exception =
              new SocketCommunicationException(
                  "Response was not received after "
                      + configuration.maxSocketTimeoutInMillis()
                      + " milliseconds, to make the proxy wait longer please use"
                      + " \"mockserver.maxSocketTimeout\" system property or"
                      + " ConfigurationProperties.maxSocketTimeout(long milliseconds)",
                  e.getCause());
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          exception = ex;
        } catch (ExecutionException ex) {
          exception = ex;
        }
        try {
          consumer.accept(httpResponse, exception);
        } catch (RuntimeException throwable) {
          log.info(throwable.getMessage(), throwable);
        }
      } else {
        future.getHttpResponse().whenCompleteAsync(consumer, scheduler);
      }
    }
  }
}
