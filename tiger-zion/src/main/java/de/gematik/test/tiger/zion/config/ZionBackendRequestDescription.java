package de.gematik.test.tiger.zion.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class ZionBackendRequestDescription {

    private String url;
    private Map<String, String> headers;
    private String body;
    private String method;

    private boolean executeBeforeSelection = false;
    private Map<String, String> assignments;
}
