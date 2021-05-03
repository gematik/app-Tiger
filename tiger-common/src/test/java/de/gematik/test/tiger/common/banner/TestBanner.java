package de.gematik.test.tiger.common.banner;

import de.gematik.test.tiger.common.Ansi;
import org.junit.Test;

public class TestBanner {
    @Test
    public void testBannerOK() {
        Banner.shout("TIGER Director V1.2.0", Ansi.BOLD + Ansi.GREEN);
        Banner.shout("Starting director...", Ansi.GREEN);
        Banner.shout("abcdefghijklmnopqrstuvwxyz");
        Banner.shout("TIGER DIRECTOR V1.2.0", Ansi.BOLD + Ansi.GREEN);
        Banner.shout("STARTING DIRECTOR...", Ansi.GREEN);
        Banner.shout("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        Banner.shout("ÄäÜüÖöß?%&");
        Banner.shout("12345,67;89:0");
    }

}
