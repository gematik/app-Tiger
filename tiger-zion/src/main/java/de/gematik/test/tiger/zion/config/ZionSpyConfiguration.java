package de.gematik.test.tiger.zion.config;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZionSpyConfiguration {

    private String url;
    private String protocolToPath;
}
