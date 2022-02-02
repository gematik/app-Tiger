package de.gematik.test.tiger.common.data.config.tigerProxy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder(toBuilder = true)
@AllArgsConstructor
@Data
public class TigerBasicAuthConfiguration {

    private String username;
    private String password;

    public String toAuthorizationHeaderValue() {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password)
            .getBytes(StandardCharsets.US_ASCII));
    }
}
