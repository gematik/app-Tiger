package de.gematik.test.tiger.proxy.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
