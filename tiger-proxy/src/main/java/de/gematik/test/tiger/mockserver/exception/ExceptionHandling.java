/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.exception;

import de.gematik.test.tiger.mockserver.httpclient.SocketConnectionException;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.internal.PlatformDependent;
import java.net.ConnectException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class ExceptionHandling {

  private static final Pattern IGNORABLE_CLASS_IN_STACK =
      Pattern.compile("^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$"); // NOSONAR
  private static final Pattern IGNORABLE_ERROR_MESSAGE =
      Pattern.compile( // NOSONAR
          "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$",
          Pattern.CASE_INSENSITIVE);

  public static <T> T handleThrowable(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      log.warn("Exception thrown", e);
      throw new RuntimeException(e);
    }
  }

  /** Closes the specified channel after all queued write requests are flushed. */
  public static void closeOnFlush(Channel ch) {
    if (ch != null && ch.isActive()) {
      ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }

  /** returns true is the exception was caused by the connection being closed */
  public static boolean connectionClosedException(Throwable throwable) {
    String message = String.valueOf(throwable.getMessage()).toLowerCase();

    // is ssl exception
    if (throwable.getCause() instanceof SSLException
        || throwable instanceof DecoderException
        || throwable instanceof NotSslRecordException) {
      return false;
    }

    // first try to match connection reset / broke peer based on the regex.
    // This is the fastest way but may fail on different jdk impls or OS's
    if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
      return false;
    }

    // Inspect the StackTraceElements to see if it was a connection reset / broken pipe or not
    StackTraceElement[] elements = throwable.getStackTrace();
    for (StackTraceElement element : elements) {
      String classname = element.getClassName();
      String methodname = element.getMethodName();

      // skip all classes that belong to the io.netty package
      if (classname.startsWith("io.netty.")) {
        continue;
      }

      // check if the method name is read if not skip it
      if (!"read".equals(methodname)) {
        continue;
      }

      // This will also match against SocketInputStream which is used by openjdk 7 and maybe
      // also others
      if (IGNORABLE_CLASS_IN_STACK.matcher(classname).matches()) {
        return false;
      }

      try {
        // No match by now. Try to load the class via classloader and inspect it.
        // This is mainly done as other JDK implementations may differ in name of
        // the impl.
        Class<?> clazz =
            PlatformDependent.getClassLoader(ExceptionHandling.class).loadClass(classname);

        if (SocketChannel.class.isAssignableFrom(clazz)
            || DatagramChannel.class.isAssignableFrom(clazz)) {
          return false;
        }

        // also match against SctpChannel via String matching as it may not present.
        if (PlatformDependent.javaVersion() >= 7
            && "com.sun.nio.sctp.SctpChannel".equals(clazz.getSuperclass().getName())) { // NOSONAR
          return false;
        }
      } catch (ClassNotFoundException e) {
        // This should not happen just ignore
      }
    }
    return true;
  }

  private static final List<Class<? extends Exception>> SSL_HANDSHAKE_FAILURE_CLASSES =
      Arrays.asList(
          SSLException.class,
          SSLHandshakeException.class,
          CertPathValidatorException.class,
          SignatureException.class);

  public static boolean sslHandshakeException(Throwable throwable) {
    for (Class<? extends Throwable> cause : getCauses(throwable)) {
      if (SSL_HANDSHAKE_FAILURE_CLASSES.contains(cause)) {
        return true;
      }
    }
    return false;
  }

  private static final List<Class<? extends Exception>> CONNECTION_EXCEPTION_CLASSES =
      Arrays.asList(SocketConnectionException.class, ConnectException.class);

  public static boolean connectionException(Throwable throwable) {
    for (Class<? extends Throwable> cause : getCauses(throwable)) {
      if (CONNECTION_EXCEPTION_CLASSES.contains(cause)) {
        return true;
      }
    }
    return false;
  }

  private static List<Class<? extends Throwable>> getCauses(final Throwable throwable) {
    if (throwable.getCause() != null) {
      if (throwable.getClass().equals(throwable.getCause().getClass())) {
        return new ArrayList<>(Collections.singletonList(throwable.getClass()));
      } else {
        final List<Class<? extends Throwable>> causes = getCauses(throwable.getCause());
        causes.add(throwable.getClass());
        return causes;
      }
    } else {
      return new ArrayList<>(Collections.singletonList(throwable.getClass()));
    }
  }
}
