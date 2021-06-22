package de.gematik.test.tiger.proxy.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder(toBuilder = true)
@AllArgsConstructor
@Data
public class TigerRoute {

    private String id;
    private String from;
    private String to;
    private boolean disableRbelLogging;
}
