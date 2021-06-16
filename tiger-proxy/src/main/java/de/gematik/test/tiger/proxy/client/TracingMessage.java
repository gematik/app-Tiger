package de.gematik.test.tiger.proxy.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TracingMessage {

    private String header;
    private byte[] body;
}
