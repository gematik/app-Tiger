package de.gematik.test.tiger.proxy.tracing;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TracingMessage {

    private byte[] rawContent;
}
