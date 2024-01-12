package de.gematik.test.tiger.mockserver.log.model;

import static de.gematik.test.tiger.mockserver.formatting.StringFormatter.formatLogMessage;
import static de.gematik.test.tiger.mockserver.model.HttpRequest.request;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.matchers.HttpRequestMatcher;
import de.gematik.test.tiger.mockserver.matchers.MatchDifference;
import de.gematik.test.tiger.mockserver.matchers.TimeToLive;
import de.gematik.test.tiger.mockserver.matchers.Times;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.time.EpochService;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.slf4j.event.Level;

@Data
public class LogEntry {

  private static final RequestDefinition[] EMPTY_REQUEST_DEFINITIONS = new RequestDefinition[0];
  private static final RequestDefinition[] DEFAULT_REQUESTS_DEFINITIONS = {request()};
  private int hashCode;
  private String id;
  private String correlationId;
  private Integer port;
  private Level logLevel = Level.INFO;
  private boolean alwaysLog = false;
  private long epochTime = EpochService.currentTimeMillis();
  private String timestamp;
  private LogMessageType type;
  private RequestDefinition[] httpRequests;
  private RequestDefinition[] httpUpdatedRequests;
  private HttpResponse httpResponse;
  private HttpResponse httpUpdatedResponse;
  private Expectation expectation;
  private String expectationId;
  private Throwable throwable;
  private Runnable consumer;
  private boolean deleted = false;

  private String messageFormat;
  private String message;
  private Object[] arguments;
  private String because;

  private LogEntry setId(String id) {
    this.id = id;
    return this;
  }

  @JsonIgnore
  public String id() {
    if (id == null) {
      id = UUIDService.getUUID();
    }
    return id;
  }

  public Level getLogLevel() {
    return logLevel;
  }

  public LogEntry setLogLevel(Level logLevel) {
    this.logLevel = logLevel;
    if (type == null) {
      type = LogMessageType.valueOf(logLevel.name());
    }
    return this;
  }

  public boolean isAlwaysLog() {
    return alwaysLog;
  }

  public LogEntry setAlwaysLog(boolean alwaysLog) {
    this.alwaysLog = alwaysLog;
    return this;
  }

  public long getEpochTime() {
    return epochTime;
  }

  public LogEntry setEpochTime(long epochTime) {
    this.epochTime = epochTime;
    this.timestamp = null;
    return this;
  }

  public String getTimestamp() {
    if (timestamp == null) {
      timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(epochTime));
    }
    return timestamp;
  }

  public LogMessageType getType() {
    return type;
  }

  public LogEntry setType(LogMessageType type) {
    this.type = type;
    return this;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public LogEntry setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  public LogEntry setPort(Integer port) {
    this.port = port;
    return this;
  }

  public Integer getPort() {
    return port;
  }

  @JsonIgnore
  public RequestDefinition[] getHttpRequests() {
    if (httpRequests == null) {
      return EMPTY_REQUEST_DEFINITIONS;
    } else {
      return httpRequests;
    }
  }

  @JsonIgnore
  public RequestDefinition[] getHttpUpdatedRequests() {
    if (httpRequests == null) {
      return EMPTY_REQUEST_DEFINITIONS;
    } else if (httpUpdatedRequests == null) {
      httpUpdatedRequests =
          Arrays.stream(httpRequests).map(this::updateBody).toArray(RequestDefinition[]::new);
      return httpUpdatedRequests;
    } else {
      return httpUpdatedRequests;
    }
  }

  @JsonIgnore
  public boolean matches(HttpRequestMatcher matcher) {
    if (matcher == null) {
      return true;
    }
    if (httpRequests == null || httpRequests.length == 0) {
      return true;
    }
    for (RequestDefinition httpRequest : httpRequests) {
      RequestDefinition request = httpRequest.cloneWithLogCorrelationId();
      if (matcher.matches(
          type == LogMessageType.RECEIVED_REQUEST ? new MatchDifference(false, request) : null,
          request)) {
        return true;
      }
    }
    return false;
  }

  public LogEntry setHttpRequests(RequestDefinition[] httpRequests) {
    this.httpRequests = httpRequests;
    return this;
  }

  public RequestDefinition getHttpRequest() {
    if (httpRequests != null && httpRequests.length > 0) {
      return httpRequests[0];
    } else {
      return null;
    }
  }

  public LogEntry setHttpRequest(RequestDefinition httpRequest) {
    if (httpRequest != null) {
      if (isNotBlank(httpRequest.getLogCorrelationId())) {
        setCorrelationId(httpRequest.getLogCorrelationId());
      }
      this.httpRequests = new RequestDefinition[] {httpRequest};
    } else {
      this.httpRequests = DEFAULT_REQUESTS_DEFINITIONS;
    }
    return this;
  }

  public HttpResponse getHttpResponse() {
    return httpResponse;
  }

  public HttpResponse getHttpUpdatedResponse() {
    if (httpResponse == null) {
      return null;
    } else if (httpUpdatedResponse == null) {
      httpUpdatedResponse = updateBody(httpResponse);
      return httpUpdatedResponse;
    } else {
      return httpUpdatedResponse;
    }
  }

  public LogEntry setHttpResponse(HttpResponse httpResponse) {
    this.httpResponse = httpResponse;
    return this;
  }

  public Expectation getExpectation() {
    return expectation;
  }

  public LogEntry setExpectation(Expectation expectation) {
    this.expectation = expectation;
    return this;
  }

  public LogEntry setExpectation(RequestDefinition httpRequest, HttpResponse httpResponse) {
    this.expectation =
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0)
            .thenRespond(httpResponse);
    return this;
  }

  public String getExpectationId() {
    return expectationId;
  }

  public LogEntry setExpectationId(String expectationId) {
    this.expectationId = expectationId;
    return this;
  }

  public boolean matchesAnyExpectationId(List<String> expectationIds) {
    if (expectationIds != null && isNotBlank(this.expectationId)) {
      return expectationIds.contains(this.expectationId);
    }
    return false;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public LogEntry setThrowable(Throwable throwable) {
    this.throwable = throwable;
    if (isBlank(messageFormat) && throwable != null) {
      messageFormat = throwable.getClass().getSimpleName();
    }
    return this;
  }

  public Runnable getConsumer() {
    return consumer;
  }

  public LogEntry setConsumer(Runnable consumer) {
    this.consumer = consumer;
    return this;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public LogEntry setDeleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  public String getMessageFormat() {
    return messageFormat;
  }

  public LogEntry setMessageFormat(String messageFormat) {
    if (isBlank(messageFormat) && throwable != null) {
      this.messageFormat = throwable.getClass().getSimpleName();
    } else {
      this.messageFormat = messageFormat;
    }
    return this;
  }

  @JsonIgnore
  public String getMessage() {
    if (message == null) {
      if (arguments != null) {
        message = formatLogMessage(messageFormat, arguments);
      } else {
        message = messageFormat;
      }
    }
    return message;
  }

  public Object[] getArguments() {
    return arguments;
  }

  public LogEntry setArguments(Object... arguments) {
    if (arguments != null) {
      this.arguments =
          Arrays.stream(arguments)
              .map(
                  argument -> {
                    if (argument instanceof HttpRequest) {
                      return updateBody((HttpRequest) argument);
                    } else if (argument instanceof HttpResponse) {
                      return updateBody((HttpResponse) argument);
                    } else if (argument == null) {
                      return "";
                    } else {
                      return argument;
                    }
                  })
              .toArray(Object[]::new);
    } else {
      this.arguments = null;
    }
    return this;
  }

  public String getBecause() {
    return because;
  }

  public LogEntry setBecause(String because) {
    this.because = because;
    return this;
  }

  private RequestDefinition updateBody(RequestDefinition requestDefinition) {
    if (requestDefinition instanceof HttpRequest) {
      HttpRequest httpRequest = (HttpRequest) requestDefinition;
      Body<?> body = httpRequest.getBody();
      if (body instanceof JsonBody) {
        try {
          return httpRequest
              .shallowClone()
              .withBody(new LogEntryBody(prettyPrintBody(body.toString())));
        } catch (RuntimeException e) {
          return httpRequest.shallowClone().withBody(new LogEntryBody(body.toString()));
        }
      } else if (body instanceof BodyWithContentType && !(body instanceof LogEntryBody)) {
        return httpRequest.shallowClone().withBody(new LogEntryBody(body.toString()));
      } else {
        return httpRequest;
      }
    } else {
      return null;
    }
  }

  private String prettyPrintBody(Object value) {
    return value.toString();
  }

  private HttpResponse updateBody(HttpResponse httpResponse) {
    if (httpResponse != null) {
      Body<?> body = httpResponse.getBody();
      if (body != null && JsonBody.class.isAssignableFrom(body.getClass())) {
        try {
          return httpResponse
              .shallowClone()
              .withBody(new LogEntryBody(prettyPrintBody(body.toString())));
        } catch (RuntimeException e) {
          return httpResponse.shallowClone().withBody(new LogEntryBody(body.toString()));
        }
      } else if (body != null && !(body instanceof LogEntryBody)) {
        return httpResponse.shallowClone().withBody(new LogEntryBody(body.toString()));
      } else {
        return httpResponse;
      }
    } else {
      return null;
    }
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public LogEntry clone() {
    return new LogEntry()
        .setId(id())
        .setType(getType())
        .setLogLevel(getLogLevel())
        .setAlwaysLog(isAlwaysLog())
        .setEpochTime(getEpochTime())
        .setCorrelationId(getCorrelationId())
        .setPort(getPort())
        .setHttpRequests(getHttpRequests())
        .setHttpResponse(getHttpResponse())
        .setExpectation(getExpectation())
        .setExpectationId(getExpectationId())
        .setMessageFormat(getMessageFormat())
        .setArguments(getArguments())
        .setBecause(getBecause())
        .setThrowable(getThrowable())
        .setConsumer(getConsumer())
        .setDeleted(isDeleted());
  }

  @Override
  public String toString() {
    try {
      return prettyPrintBody(this);
    } catch (Exception e) {
      return super.toString();
    }
  }

  public enum LogMessageType {
    RUNNABLE,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    EXCEPTION,
    CLEARED,
    RETRIEVED,
    UPDATED_EXPECTATION,
    CREATED_EXPECTATION,
    REMOVED_EXPECTATION,
    RECEIVED_REQUEST,
    EXPECTATION_RESPONSE,
    EXPECTATION_MATCHED,
    EXPECTATION_NOT_MATCHED,
    NO_MATCH_RESPONSE,
    VERIFICATION,
    VERIFICATION_FAILED,
    VERIFICATION_PASSED,
    FORWARDED_REQUEST,
    TEMPLATE_GENERATED,
    SERVER_CONFIGURATION,
    AUTHENTICATION_FAILED,
  }
}
