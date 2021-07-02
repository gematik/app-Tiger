/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.Ansi;
import org.junit.Test;

public class TestBanner {
    @Test
    public void testBannerShoutOK() { //NOSONAR
        Banner.shout("TIGER Director V1.2.0", Ansi.BOLD + Ansi.GREEN);
        Banner.shout("Starting director...", Ansi.GREEN);
        Banner.shout("abcdefghijklmnopqrstuvwxyz");
        Banner.shout("ÄäÜüÖöß?%&");
        Banner.shout("12345,67;89:0");
    }

    @Test public void testBannerStrRedOK() {
        String str = Banner.toBannerStr("TestString", Ansi.RED);
        assertThat(str).isEqualTo("\u001B[31m====================================================================================================\u001B[0m\n"
            + "\u001B[31m ___               __                     \u001B[0m\n"
            + "\u001B[31m  |    _   _  |_  (_   |_   _ .   _    _  \u001B[0m\n"
            + "\u001B[31m  |   (=  _)  |_  __)  |_  |  |  | )  (_) \u001B[0m\n"
            + "\u001B[31m                                      _/  \u001B[0m\n"
            + "\u001B[31m====================================================================================================\u001B[0m");
    }

    @Test public void testBannerStrCOLOROK() {
        String str = Banner.toBannerStrWithCOLOR("TestString", "RED");
        assertThat(str).isEqualTo("\u001B[31m====================================================================================================\u001B[0m\n"
            + "\u001B[31m ___               __                     \u001B[0m\n"
            + "\u001B[31m  |    _   _  |_  (_   |_   _ .   _    _  \u001B[0m\n"
            + "\u001B[31m  |   (=  _)  |_  __)  |_  |  |  | )  (_) \u001B[0m\n"
            + "\u001B[31m                                      _/  \u001B[0m\n"
            + "\u001B[31m====================================================================================================\u001B[0m");
    }

    @Test public void testBannerStrCOLORInvalidColorName() {
        assertThatThrownBy(() -> {
            String str = Banner.toBannerStrWithCOLOR("TestString", "NOCOLORKNOWN");
        }).isInstanceOf(AssertionError.class).hasCauseExactlyInstanceOf(NoSuchFieldException.class);
    }
}
