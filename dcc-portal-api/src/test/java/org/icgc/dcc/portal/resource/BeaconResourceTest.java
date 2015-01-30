/*
 * Copyright 2013(c) The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the GNU Public
 * License v3.0. You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.dcc.portal.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.MediaType;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.portal.mapper.BadRequestExceptionMapper;
import org.icgc.dcc.portal.model.BeaconQueryResponse;
import org.icgc.dcc.portal.model.BeaconResponse;
import org.icgc.dcc.portal.model.BeaconResponseResponse;
import org.icgc.dcc.portal.service.BeaconService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.testing.ResourceTest;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BeaconResourceTest extends ResourceTest {

  /**
   * Endpoint.
   */
  private final static String RESOURCE = "/v1/beacon/query";
  private final static int BAD_REQUEST_CODE = ClientResponse.Status.BAD_REQUEST.getStatusCode();
  private final static int OK_CODE = ClientResponse.Status.OK.getStatusCode();

  /**
   * Dependencies.
   */
  @Mock
  private BeaconService service;

  /**
   * Subject
   */
  @InjectMocks
  private BeaconResource resource;

  @Override
  protected final void setUpResources() {
    addResource(resource);
    addProvider(BadRequestExceptionMapper.class);

  }

  @Test
  public void testNormal() {
    val expected = generateDummyBeaconResponse();
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(expected);
    val response = generateResponse("1", "1111", "GRCh37", "A");
    assertThat(response.getStatus()).isEqualTo(OK_CODE);
    assertThat(response.getEntity(BeaconResponse.class)).isEqualTo(expected);
  }

  @Test
  public void testInvalidChromosomeArgs() {
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(generateDummyBeaconResponse());
    val response = generateResponse("39", "1111", "GRCh37", "A");
    assertThat(response.getStatus()).isEqualTo(BAD_REQUEST_CODE);
  }

  @Test
  public void testInvalidAlleleArgs() {
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(generateDummyBeaconResponse());
    val response = generateResponse("1", "1111", "GRCh37", "WTWT");
    assertThat(response.getStatus()).isEqualTo(BAD_REQUEST_CODE);
  }

  @Test
  public void testInvalidReferenceArgs() {
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(generateDummyBeaconResponse());
    val response = generateResponse("1", "1111", "OMG", "A");
    assertThat(response.getStatus()).isEqualTo(BAD_REQUEST_CODE);
  }

  @Test
  public void testEmptyArgs() {
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(generateDummyBeaconResponse());
    val response = generateResponse("", "1111", "GRCh37", "A");
    assertThat(response.getStatus()).isEqualTo(BAD_REQUEST_CODE);
  }

  @Test
  public void testInvalidPositionArgs() {
    when(service.query(any(String.class), anyInt(), any(String.class), any(String.class)))
        .thenReturn(generateDummyBeaconResponse());
    val response = generateResponse("MT", "1111111111", "GRCh37", "A");
    assertThat(response.getStatus()).isEqualTo(BAD_REQUEST_CODE);
  }

  private ClientResponse generateResponse(String chr, String pos, String ref, String ale) {
    return client()
        .resource(RESOURCE)
        .queryParam("chromosome", chr)
        .queryParam("position", pos)
        .queryParam("reference", ref)
        .queryParam("allele", ale)
        .accept(MediaType.APPLICATION_JSON)
        .get(ClientResponse.class);
  }

  private BeaconResponse generateDummyBeaconResponse() {
    val queryResp = new BeaconQueryResponse("A", "1", 111, "GRCh37");
    val respResp = new BeaconResponseResponse("true");
    return new BeaconResponse("whats the id", queryResp, respResp);
  }

}
