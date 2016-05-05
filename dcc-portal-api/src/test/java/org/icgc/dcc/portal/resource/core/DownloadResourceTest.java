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

package org.icgc.dcc.portal.resource.core;

import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Cookie;

import lombok.val;

import org.icgc.dcc.download.client.DefaultDownloadClient;
import org.icgc.dcc.download.client.io.AccessPermission;
import org.icgc.dcc.download.client.io.DownloadFileSystem;
import org.icgc.dcc.download.core.model.DownloadDataType;
import org.icgc.dcc.download.core.model.JobProgress;
import org.icgc.dcc.download.core.model.JobStatus;
import org.icgc.dcc.download.core.model.TaskProgress;
import org.icgc.dcc.portal.auth.UserAuthProvider;
import org.icgc.dcc.portal.auth.UserAuthenticator;
import org.icgc.dcc.portal.auth.oauth.OAuthClient;
import org.icgc.dcc.portal.config.PortalProperties.CrowdProperties;
import org.icgc.dcc.portal.config.PortalProperties.DownloadProperties;
import org.icgc.dcc.portal.mapper.BadRequestExceptionMapper;
import org.icgc.dcc.portal.model.User;
import org.icgc.dcc.portal.service.DonorService;
import org.icgc.dcc.portal.service.ForbiddenAccessException;
import org.icgc.dcc.portal.service.NotFoundException;
import org.icgc.dcc.portal.service.SessionService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Stage;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.testing.ResourceTest;

@RunWith(MockitoJUnitRunner.class)
public class DownloadResourceTest extends ResourceTest {

  private final static String RESOURCE = "/v1/download";

  private final SessionService sessionService = new SessionService();

  @Mock
  private DonorService donorService;
  @Mock
  private DefaultDownloadClient downloader;
  @Mock
  private OAuthClient oauthClient;
  @Mock
  private DownloadFileSystem fs;
  @Mock
  private DownloadProperties properties;

  private final UUID sessionToken = UUID.randomUUID();
  private final User user = new User(null, sessionToken);

  @Before
  public void setUp() throws Exception {
    user.setDaco(true);
    sessionService.putUser(sessionToken, user);
  }

  @Override
  protected final void setUpResources() {
    when(downloader.isServiceAvailable()).thenReturn(true);
    addResource(new DownloadResource(donorService, fs, Stage.PRODUCTION, downloader, properties));
    addProvider(BadRequestExceptionMapper.class);
    addProvider(new UserAuthProvider(new UserAuthenticator(sessionService, oauthClient), "openid"));
  }

  @Test
  public void testPublicDataAccessFile() throws IOException {
    when(fs.getAccessPermission(any(File.class))).thenReturn(AccessPermission.UNCHECKED);
    when(fs.isFile(any(File.class))).thenReturn(true);
    when(fs.exists(any(File.class))).thenReturn(true);
    when(fs.createInputStream((any(File.class)), anyInt())).thenReturn(new ByteArrayInputStream("test".getBytes()));

    ClientResponse response = client()
        .resource(RESOURCE)
        .queryParam("fn", "filename.txt.gz")
        .queryParam("filters", "")
        .get(ClientResponse.class);

    verify(fs, times(1)).createInputStream((any(File.class)), anyInt());
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void testOpenDataAccessFile() throws IOException {
    when(fs.getAccessPermission(any(File.class))).thenReturn(AccessPermission.OPEN);
    when(fs.createInputStream((any(File.class)), anyInt())).thenReturn(new ByteArrayInputStream("test".getBytes()));
    when(fs.isFile(any(File.class))).thenReturn(true);
    when(fs.exists(any(File.class))).thenReturn(true);

    ClientResponse response = client()
        .resource(RESOURCE)
        .queryParam("fn", "filename_open.txt.gz")
        .queryParam("filters", "")
        .get(ClientResponse.class);

    verify(fs, times(1)).createInputStream((any(File.class)), anyInt());
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void testControlledDataAccessFile() throws IOException {
    when(fs.getAccessPermission(any(File.class))).thenReturn(AccessPermission.CONTROLLED);
    when(fs.createInputStream((any(File.class)), anyInt())).thenReturn(new ByteArrayInputStream("test".getBytes()));
    when(fs.isFile(any(File.class))).thenReturn(true);
    when(fs.exists(any(File.class))).thenReturn(true);

    val response = client()
        .resource(RESOURCE)
        .queryParam("fn", "filename_control.txt.gz")
        .queryParam("filters", "")
        .cookie(new Cookie(CrowdProperties.SESSION_TOKEN_NAME, sessionToken.toString()))
        .get(ClientResponse.class);

    verify(fs, times(1)).createInputStream((any(File.class)), anyInt());
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test(expected = ForbiddenAccessException.class)
  public void testDeniedControlledDataAccessFile() throws IOException {
    when(fs.isFile(any(File.class))).thenReturn(true);
    when(fs.getAccessPermission(any(File.class))).thenReturn(AccessPermission.CONTROLLED);

    client()
        .resource(RESOURCE)
        .queryParam("fn", "filename_control.txt.gz")
        .queryParam("filters", "")
        // no token
        .get(ClientResponse.class);
  }

  @Test
  public void testOpenDataAccessStream() throws IOException {
    when(downloader.streamArchiveInTarGz(any(OutputStream.class), anyString(), any())).thenReturn(true);
    val testId = "TESTID";
    val jobProgress = createSuccessfulJobProgress();
    when(downloader.getJobsProgress(anySetOf(String.class))).thenReturn(ImmutableMap.of(testId, jobProgress));

    val response = client()
        .resource(RESOURCE + "/" + testId)
        .queryParam("filters", "")
        .queryParam("info", "[{\"key\":\"SSM\",\"value\":\"TSV\"}]")
        .get(ClientResponse.class);

    val selection = ImmutableList.of(DownloadDataType.SSM_OPEN);
    verify(downloader).streamArchiveInTarGz(any(OutputStream.class), anyString(),
        argThat(new SelectionEntryArgumentMatcher(selection)));
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void testNoArgument() throws IOException {
    ClientResponse response = client()
        .resource(RESOURCE)
        .get(ClientResponse.class);
    assertEquals(400, response.getStatus());
  }

  @Test
  public void testEmptyArgument() throws IOException {
    ClientResponse response = client()
        .resource(RESOURCE)
        .queryParam("fn", "")
        .get(ClientResponse.class);
    assertEquals(400, response.getStatus());
  }

  @Test(expected = NotFoundException.class)
  public void testDeniedControlledDataAccessStream() throws IOException {
    when(fs.isFile(any(File.class))).thenThrow(new FileNotFoundException());
    // try to access control data without proper authentication
    client()
        .resource(RESOURCE)
        .queryParam("fn", "somefiles")
        .get(ClientResponse.class);
  }

  // we don't have controlled access authentication
  @Test
  @Ignore
  public void testcontrolAccessStream() throws IOException {
    when(
        downloader.streamArchiveInTarGz(any(OutputStream.class), anyString(),
            Matchers.<List<DownloadDataType>> any()))
        .thenReturn(true);
    ClientResponse response = client()
        .resource(RESOURCE)
        .queryParam("filters", "")
        .queryParam("info", "[{\"key\":\"ssm\",\"value\":\"TSV\"}]")
        .cookie(new Cookie(CrowdProperties.SESSION_TOKEN_NAME, sessionToken.toString()))
        .get(ClientResponse.class);
    List<DownloadDataType> selection =
        ImmutableList.of(DownloadDataType.SSM_CONTROLLED);
    verify(downloader).streamArchiveInTarGz(any(OutputStream.class), anyString(),
        argThat(new SelectionEntryArgumentMatcher(selection)));
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void test_getArchive_found() throws IOException {
    when(downloader.streamArchiveInTarGz(any(OutputStream.class), anyString(), any())).thenReturn(true);
    val testId = "TESTID";
    val jobStatus = createSuccessfulJobProgress();
    when(downloader.getJobsProgress(anySetOf(String.class))).thenReturn(ImmutableMap.of(testId, jobStatus));

    ClientResponse response = client()
        .resource(RESOURCE + "/" + testId)
        .queryParam("filters", "")
        .queryParam("info", "")
        .get(ClientResponse.class);

    verify(downloader).streamArchiveInTarGz(any(OutputStream.class), anyString(), any());
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void testGetArchiveBadRequest() {
    val response = client()
        .resource(RESOURCE)
        .queryParam("filters", "")
        .queryParam("info", "")
        .get(ClientResponse.class);

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testGetJobStatusWithOverCapacity() {
    Map<String, Object> response = client()
        .resource(RESOURCE + "/status")
        .get(new GenericType<Map<String, Object>>() {});
    assertThat(response.get("serviceStatus")).isEqualTo(true);
  }

  @Test
  public void testGetJobStatusWithUnavailable() {
    when(downloader.isServiceAvailable()).thenReturn(false);
    Map<String, Object> response = client()
        .resource(RESOURCE + "/status")
        .queryParam("filters", "")
        .queryParam("info", "")
        .get(new GenericType<Map<String, Object>>() {});
    assertThat(response.get("serviceStatus")).isEqualTo(false);
  }

  private static JobProgress createSuccessfulJobProgress() {
    return new JobProgress(JobStatus.SUCCEEDED,
        ImmutableMap.of(DownloadDataType.SSM_OPEN, new TaskProgress(1, 1)));
  }

  private static final class SelectionEntryArgumentMatcher extends ArgumentMatcher<List<DownloadDataType>> {

    List<DownloadDataType> selection;

    public SelectionEntryArgumentMatcher(List<DownloadDataType> selection) {
      this.selection = selection;
    }

    @Override
    public boolean matches(Object argument) {
      @SuppressWarnings("unchecked")
      List<DownloadDataType> selection = (List<DownloadDataType>) argument;
      return this.selection.equals(selection);
    }
  }

}
