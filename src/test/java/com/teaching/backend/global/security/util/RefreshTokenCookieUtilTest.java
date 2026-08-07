package com.teaching.backend.global.security.util;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieUtilTest {

    @Test
    void domain_설정된_환경에서는_host_only_쿠키도_만료시킨다() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil();
        ReflectionTestUtils.setField(util, "secure", true);
        ReflectionTestUtils.setField(util, "domain", "teachingg.site");

        MockHttpServletResponse response = new MockHttpServletResponse();
        util.issue(response, "test-token", 1000);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
    }

    @Test
    void domain_없으면_host_only_쿠키_하나만_나간다() {
        RefreshTokenCookieUtil util = new RefreshTokenCookieUtil();
        ReflectionTestUtils.setField(util, "secure", false);
        ReflectionTestUtils.setField(util, "domain", "");

        MockHttpServletResponse response = new MockHttpServletResponse();
        util.issue(response, "test-token", 1000);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(1);
    }
}