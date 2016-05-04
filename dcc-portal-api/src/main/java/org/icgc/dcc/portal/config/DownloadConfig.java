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

import static com.google.common.base.Preconditions.checkArgument;
import static org.icgc.dcc.download.client.io.DownloadFileSystem.DEFAULT_ROOT_DIR;

import java.io.IOException;

import lombok.SneakyThrows;
import lombok.val;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.icgc.dcc.download.client.DefaultDownloadClient;
import org.icgc.dcc.download.client.DownloadClient;
import org.icgc.dcc.download.client.HttpDownloadClient;
import org.icgc.dcc.download.client.NoOpDownloadClient;
import org.icgc.dcc.download.client.io.ArchiveOutputStream;
import org.icgc.dcc.download.client.io.CurrentProjectSimLink;
import org.icgc.dcc.download.client.io.DownloadFileSystem;
import org.icgc.dcc.portal.config.PortalProperties.DownloadProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Lazy
@Configuration
public class DownloadConfig {

  @Bean
  @SneakyThrows
  public DownloadClient downloadClient(DownloadProperties properties) {
    if (properties.isEnabled() == false) {
      return new NoOpDownloadClient();
    }

    val dynamicDownloadPath = new Path(properties.getDynamicRootPath());
    val out = new ArchiveOutputStream(dynamicDownloadPath, getFileSystem(properties));
    val httpClient = new HttpDownloadClient(properties.getServerUrl());

    return new DefaultDownloadClient(out, httpClient);
  }

  @Bean
  @SneakyThrows
  public DownloadFileSystem downloadFileSystem(DownloadProperties properties) {
    if (properties.isEnabled() == false) {
      return null;
    }

    val rootDir = properties.getUri() + properties.getStaticRootPath();
    val rootUri = (new Path(rootDir)).toUri();
    val rootPath = rootDir != null ? new Path(rootUri.getPath()) : new Path(DEFAULT_ROOT_DIR);

    val fileSystem = getFileSystem(properties);
    if (!fileSystem.exists(rootPath)) {
      throw new IOException("root directory doesn't exist:" + rootPath);
    }
    fileSystem.setWorkingDirectory(rootPath);

    return new DownloadFileSystem(currentProjectSimLink(properties), fileSystem, rootPath);
  }

  private static CurrentProjectSimLink currentProjectSimLink(DownloadProperties properties) {
    val currentSymlink = properties.getCurrentReleaseSymlink();
    String[] currentReleaseLink = currentSymlink.split(" ");
    checkArgument(currentReleaseLink.length == 2, "Invalid argument for currentSymlink:" + currentSymlink);

    return new CurrentProjectSimLink(currentReleaseLink[0], currentReleaseLink[1]);
  }

  private static FileSystem getFileSystem(DownloadProperties properties) throws IOException {
    val hadoopConfig = new org.apache.hadoop.conf.Configuration();
    hadoopConfig.set("fs.defaultFS", properties.getUri());

    return FileSystem.get(hadoopConfig);
  }

}
