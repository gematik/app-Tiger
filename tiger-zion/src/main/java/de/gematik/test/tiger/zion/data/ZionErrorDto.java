package de.gematik.test.tiger.zion.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZionErrorDto {
    private final String errorMessage;
    private final String errorType;
    private final String stacktrace;

}
