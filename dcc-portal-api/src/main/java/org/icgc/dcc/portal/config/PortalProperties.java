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

package org.icgc.dcc.portal.config;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.inject.Stage.DEVELOPMENT;
import static org.icgc.dcc.downloader.core.ArchiverConstant.ARCHIVE_CURRENT_RELEASE;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.URL;
import org.icgc.dcc.portal.browser.model.DataSource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Stage;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.db.DatabaseConfiguration;

@Data
@EqualsAndHashCode(callSuper = false)
public class PortalProperties extends Configuration {

  @Valid
  @JsonProperty
  CrowdProperties crowd = new CrowdProperties();

  @Valid
  @JsonProperty
  ElasticSearchProperties elastic = new ElasticSearchProperties();

  @Valid
  @JsonProperty
  BrowserProperties browser = new BrowserProperties();

  @Valid
  @JsonProperty
  MailProperties mail = new MailProperties();

  @Valid
  @JsonProperty
  DownloadProperties download = new DownloadProperties();

  @Valid
  @JsonProperty
  ICGCProperties icgc = new ICGCProperties();

  @Valid
  @JsonProperty
  HazelcastProperties hazelcast = new HazelcastProperties();

  @Valid
  @JsonProperty
  CacheProperties cache = new CacheProperties();

  @Valid
  @JsonProperty
  WebProperties web = new WebProperties();

  @Valid
  @JsonProperty
  ReleaseProperties release = new ReleaseProperties();

  @Valid
  @JsonProperty
  SetOperationProperties setOperation = new SetOperationProperties();

  @Valid
  @NotNull
  @JsonProperty
  DatabaseConfiguration database = new DatabaseConfiguration();

  @Data
  public static class BrowserProperties {

    @JsonProperty
    List<DataSource> dataSources;

  }

  @Data
  public static class CacheProperties {

    @JsonProperty
    boolean enableLastModified;

    @JsonProperty
    List<String> excludeLastModified = newArrayList();

    @JsonProperty
    boolean enableETag;

    @JsonProperty
    List<String> excludeETag = newArrayList();

  }

  @Data
  public static class CrowdProperties {

    /**
     * The cookie name for the session token generated by the portal-api
     */
    public static final String SESSION_TOKEN_NAME = "dcc_portal_token";

    /**
     * The cookie name generated by the ICGC SSO authenticator
     */
    public static final String CUD_TOKEN_NAME = "crowd.token_key";

    @JsonProperty
    String ssoUrl;

  }

  @Data
  public static class DownloadProperties {

    @JsonProperty
    boolean enabled = true;

    @JsonProperty
    String dynamicRootPath = "/icgc/download/dynamic";

    @JsonProperty
    String staticRootPath = "/icgc/download/static";

    @JsonProperty
    String uri = "";

    @JsonProperty
    Stage stage = DEVELOPMENT;

    @JsonProperty
    int maxUsers = 20;

    @JsonProperty
    String currentReleaseSymlink = "ent /dev";

    @JsonProperty
    int maxDownloadSizeInMB = 400;

    @JsonProperty
    String releaseName = ARCHIVE_CURRENT_RELEASE;

    @JsonProperty
    String quorum = "localhost";

    @JsonProperty
    String oozieUrl = "http://localhost:11000/oozie";

    @JsonProperty
    String supportEmailAddress = "***REMOVED***";

    @JsonProperty
    String appPath = "***REMOVED***";

    @JsonProperty
    byte capacityThreshold = 20;

  }

  @Data
  public static class ElasticSearchProperties {

    @JsonProperty
    String indexName = "dcc-release-release5";

    @JsonProperty
    List<ElasticSearchNodeAddress> nodeAddresses = newArrayList();

    @Getter
    @ToString
    public static class ElasticSearchNodeAddress {

      @JsonProperty
      String host = "localhost";

      @Min(1)
      @Max(65535)
      @JsonProperty
      int port = 9300;

    }

  }

  @Data
  public static class HazelcastProperties {

    @JsonProperty
    boolean enabled = false;

    @JsonProperty
    String groupName;

    @JsonProperty
    String groupPassword;

    @JsonProperty
    int usersCacheTTL;

    @JsonProperty
    int openidAuthTTL;

  }

  @Data
  public static class ICGCProperties {

    @JsonProperty
    String cgpUrl;

    @JsonProperty
    String shortUrl;

    @JsonProperty
    String cudUrl;

    @JsonProperty
    String cudAppId;

    @JsonProperty
    String cudUser;

    @JsonProperty
    String cudPassword;

    @JsonProperty
    String consumerKey;

    @JsonProperty
    String consumerSecret;

    @JsonProperty
    String accessToken;

    @JsonProperty
    String accessSecret;

    @JsonProperty
    Boolean enableHttpLogging;

    @JsonProperty
    Boolean enableStrictSSL;

  }

  @Data
  public static class MailProperties {

    boolean enabled = false;

    String smtpServer = "***REMOVED***";
    int smtpPort = 25;

    @Email
    String senderEmail = "no-reply@oicr.on.ca";

    String senderName = "DCC Portal";

    String recipientEmail = "***REMOVED***";
  }

  @Data
  public static class ReleaseProperties {

    @JsonProperty
    @NotEmpty
    String releaseDate;

    @JsonProperty
    @Min(1)
    int dataVersion;
  }

  @Data
  public static class SetOperationProperties {

    @JsonProperty
    @NotEmpty
    String demoListUuid;

    @JsonProperty
    @NotEmpty
    String demoListFilterParam;

    @JsonProperty
    int maxPreviewNumberOfHits;
    @JsonProperty
    int maxNumberOfHits;
    @JsonProperty
    int maxMultiplier;
  }

  @Data
  public static class WebProperties {

    @JsonProperty
    @NotEmpty
    @URL
    String baseUrl;

  }

}
