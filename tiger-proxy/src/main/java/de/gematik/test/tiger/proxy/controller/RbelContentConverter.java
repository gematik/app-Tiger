/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 */
package de.gematik.test.tiger.proxy.controller;

import de.gematik.rbellogger.util.RbelContent;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RbelContentConverter extends AbstractHttpMessageConverter<RbelContent> {

  public RbelContentConverter() {
    super(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL);
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return RbelContent.class.isAssignableFrom(clazz);
  }

  @Override
  protected RbelContent readInternal(
      Class<? extends RbelContent> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return RbelContent.from(inputMessage.getBody());
  }

  @Override
  protected void writeInternal(RbelContent input, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    try (var in = input.toInputStream()) {
      in.transferTo(outputMessage.getBody());
    }
  }
}
