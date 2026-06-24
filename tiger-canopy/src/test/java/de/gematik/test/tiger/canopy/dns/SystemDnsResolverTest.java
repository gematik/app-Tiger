/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.canopy.dns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Type;

class SystemDnsResolverTest {

  @Test
  void delegatesToUpstreamResolver() throws Exception {
    Resolver delegate = Mockito.mock(Resolver.class);
    Message query =
        Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN));
    Message upstream = new Message(query.getHeader().getID());
    when(delegate.send(any())).thenReturn(upstream);

    SystemDnsResolver resolver = new SystemDnsResolver(delegate);
    Message response = resolver.resolve(query);

    assertThat(response).isSameAs(upstream);
  }

  @Test
  void servfailOnIoException() throws Exception {
    Resolver delegate = Mockito.mock(Resolver.class);
    Message query =
        Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN));
    when(delegate.send(any())).thenThrow(new IOException("upstream down"));

    SystemDnsResolver resolver = new SystemDnsResolver(delegate);
    Message response = resolver.resolve(query);

    assertThat(response.getHeader().getRcode()).isEqualTo(Rcode.SERVFAIL);
  }

  @Test
  void buildsDelegateFromExplicitUpstreamServers() throws Exception {
    CanopyConfiguration cfg = new CanopyConfiguration();
    cfg.setUpstreamDnsServers(List.of("127.0.0.1", "127.0.0.2"));

    SystemDnsResolver resolver = new SystemDnsResolver(cfg);

    assertThat(resolver).isNotNull();
  }

  @Test
  void buildsDelegateFromResolvConfFallback() throws Exception {
    CanopyConfiguration cfg = new CanopyConfiguration();

    SystemDnsResolver resolver = new SystemDnsResolver(cfg);

    assertThat(resolver).isNotNull();
  }
}
