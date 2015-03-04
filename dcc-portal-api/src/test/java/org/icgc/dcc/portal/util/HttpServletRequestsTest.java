/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.portal.util;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.icgc.dcc.portal.util.HttpServletRequests.getHttpRequestCallerInfo;

import javax.servlet.http.HttpServletRequest;

import lombok.Builder;
import lombok.Value;
import lombok.val;

import org.icgc.dcc.portal.util.HttpServletRequestsTest.MockHttpRequestValue.MockHttpRequestValueBuilder;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Test suite for HttpServletRequests
 */
public class HttpServletRequestsTest {

  @Value
  @Builder
  public static class MockHttpRequestValue {

    String localHostName;
    String localHostIp;
    String userIp;
    String proxyHostName;
    String proxyHostIp;

  }

  private final static String MESSAGE_FORMAT_TEMPLATE = "Web request received on %1$s (%2$s) from %3$s via %4$s (%5$s)";
  private final static MockHttpRequestValueBuilder BUILDER = MockHttpRequestValue.builder();

  private static MockHttpRequestValue createMockValue(final String localHostName, final String localHostIp,
      final String userIp, final String proxyHostName, final String proxyHostIp) {
    return BUILDER
        .localHostName(localHostName)
        .localHostIp(localHostIp)
        .proxyHostName(proxyHostName)
        .proxyHostIp(proxyHostIp)
        .userIp(userIp)
        .build();
  }

  private static HttpServletRequest createMock(final MockHttpRequestValue value) {
    val request = new MockHttpServletRequest(GET, "/foo");

    request.setLocalName(value.localHostName);
    request.setLocalAddr(value.localHostIp);
    request.setRemoteHost(value.proxyHostName);
    request.setRemoteAddr(value.proxyHostIp);
    request.addHeader(X_FORWARDED_FOR, value.userIp);

    return request;
  }

  private static void test(final String localHostName, final String localHostIp,
      final String userIp, final String proxyHostName, final String proxyHostIp) {
    val mockValue = createMockValue(localHostName, localHostIp, userIp, proxyHostName, proxyHostIp);
    val request = createMock(mockValue);
    val expectedResult =
        String.format(MESSAGE_FORMAT_TEMPLATE, localHostName, localHostIp, userIp, proxyHostName, proxyHostIp);

    System.out.println("Expected string is: " + expectedResult);

    assertThat(getHttpRequestCallerInfo(request)).isEqualTo(expectedResult);
  }

  @Test
  public void testRequest1() {
    test("webserver1", "1.1.1.1", "1.1.1.3", "proxy1", "1.1.1.2");
  }

  @Test
  public void testRequest2() {
    test("webserver1", "0.0.0.1", "1.1.1.3", "proxy1", "1.1.1.2");
  }

}
