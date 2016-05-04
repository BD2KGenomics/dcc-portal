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

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.net.HttpHeaders.ACCEPT_RANGES;
import static com.google.common.net.HttpHeaders.CONTENT_DISPOSITION;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_RANGE;
import static com.google.common.net.HttpHeaders.RANGE;
import static com.sun.jersey.core.header.ContentDisposition.type;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.ok;
import static org.elasticsearch.common.collect.Maps.immutableEntry;
import static org.icgc.dcc.common.core.util.stream.Collectors.toImmutableList;
import static org.icgc.dcc.common.core.util.stream.Collectors.toImmutableMap;
import static org.icgc.dcc.common.core.util.stream.Collectors.toImmutableSet;
import static org.icgc.dcc.portal.util.JsonUtils.parseDownloadDataTypeNames;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import lombok.Cleanup;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.icgc.dcc.download.client.DownloadClient;
import org.icgc.dcc.download.client.io.DownloadFileSystem;
import org.icgc.dcc.download.core.model.DownloadDataType;
import org.icgc.dcc.download.core.model.JobProgress;
import org.icgc.dcc.download.core.model.TaskProgress;
import org.icgc.dcc.portal.config.PortalProperties.DownloadProperties;
import org.icgc.dcc.portal.download.ControlledAccessPredicate;
import org.icgc.dcc.portal.download.DownloadDataTypes;
import org.icgc.dcc.portal.download.JobInfo;
import org.icgc.dcc.portal.download.OpenAccessPredicate;
import org.icgc.dcc.portal.download.ServiceStatus;
import org.icgc.dcc.portal.model.FileInfo;
import org.icgc.dcc.portal.model.FiltersParam;
import org.icgc.dcc.portal.model.IdsParam;
import org.icgc.dcc.portal.model.Query;
import org.icgc.dcc.portal.model.User;
import org.icgc.dcc.portal.service.BadRequestException;
import org.icgc.dcc.portal.service.DonorService;
import org.icgc.dcc.portal.service.ForbiddenAccessException;
import org.icgc.dcc.portal.service.NotFoundException;
import org.icgc.dcc.portal.service.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Stage;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;

@Component
@Slf4j
@Api(value = "/v1/download", description = "Resources relating to archive downloading")
@Path("/v1/download")
@Consumes(APPLICATION_JSON)
public class DownloadResource {

  /**
   * Constants.
   */
  private static final String IS_CONTROLLED = "isControlled";
  private static final String APPLICATION_GZIP = "application/x-gzip";
  private static final String APPLICATION_TAR = "application/x-tar";

  // Additional states for UI
  private static final String FOUND_STATUS = "FOUND";
  private static final String NOT_FOUND_STATUS = "NOT_FOUND";

  private static final String INDIVIDUAL_TYPE_ARCHIVE_EXTENSION = ".tsv.gz";
  private static final String FULL_ARCHIVE_EXTENSION = ".tar";

  /**
   * Dependencies.
   */
  private final DonorService donorService;
  private final DownloadFileSystem fs;
  private final DownloadClient downloadClient;
  private final String defaultEmail;

  /**
   * Configuration.
   */
  private final Stage env;

  @Autowired
  public DownloadResource(DonorService donorService, DownloadFileSystem fs, Stage env, DownloadClient downloadClient,
      DownloadProperties properties) {
    this.donorService = donorService;
    this.fs = fs;
    this.env = env;
    this.downloadClient = downloadClient;
    this.defaultEmail = properties.getSupportEmailAddress();
    log.debug("Download Resource in {} mode", env);
  }

  @GET
  @Timed
  @Produces(APPLICATION_JSON)
  @Path("/status")
  @ApiOperation("Get download service availability")
  public ServiceStatus getServiceStatus() {
    return new ServiceStatus(downloadClient.isServiceAvailable());
  }

  @GET
  @Timed
  @Path("/submit")
  @Produces(APPLICATION_JSON)
  @ApiOperation("Submit job to request archive generation")
  public JobInfo submitJob(
      @Auth(required = false) User user,
      @ApiParam(value = "Filter the search results", required = false) @QueryParam("filters") @DefaultValue("{}") FiltersParam filters,
      @ApiParam(value = "Archive param") @QueryParam("info") @DefaultValue("") String info,
      @ApiParam(value = "user email address", required = false, access = "internal") @QueryParam("email") @DefaultValue("") String email,
      @ApiParam(value = "download url", required = false, access = "internal") @QueryParam("downloadUrl") @DefaultValue("") String downloadUrl,
      @ApiParam(value = "UI representation of the filter string", required = false, access = "internal") @QueryParam("uiQueryStr") @DefaultValue("{}") String uiQueryStr
      ) {
    ensureServiceRunning();
    val donorIds = resolveDonorIds(filters);

    try {
      val jobInfo = org.icgc.dcc.download.core.model.JobInfo.builder()
          .filter(filters.toString())
          .email(email.equals("") ? defaultEmail : email)
          .isControlled(isAuthorized(user))
          .startTime(currentTimeMillis())
          .uiQueryStr(uiQueryStr)
          .build();

      val downloadId = downloadClient.submitJob(
          donorIds,
          resolveDownloadDataTypes(user, info),
          jobInfo,
          email);

      return new JobInfo(downloadId);
    } catch (Exception e) {
      log.error("Job submission failed.", e);
      throw new NotFoundException("Sorry, job submission failed.", "download");
    }
  }

  @GET
  @Timed
  @Produces(APPLICATION_JSON)
  @Path("/{downloadId}/cancel")
  @ApiOperation("Cancel a download job associated with the supplied 'download id'")
  public Map<String, Object> cancelJob(
      @Auth(required = false) User user,
      @ApiParam(value = "download id") @PathParam("downloadId") String downloadId) throws BadRequestException {
    ensureResoucesAccessPermissions(user, ImmutableSet.of(downloadId));
    val jobProgress = downloadClient.getJobsProgress(ImmutableSet.of(downloadId));
    downloadClient.cancelJob(downloadId);

    return standardizeStatus(downloadId, jobProgress.get(downloadId));
  }

  @GET
  @Timed
  @Produces(APPLICATION_JSON)
  @Path("/{downloadIds}/status")
  @ApiOperation("Get download service availability")
  public List<Map<String, Object>> getJobStatus(
      @Auth(required = false) User user,
      @ApiParam(value = "download id") @PathParam("downloadIds") String downloadIds) {
    val ids = ImmutableSet.copyOf(downloadIds.split(",", -1));
    ensureResoucesAccessPermissions(user, ids);

    val jobProgresses = downloadClient.getJobsProgress(ids);
    val jobStatuses = jobProgresses.entrySet().stream()
        .map(e -> standardizeStatus(e.getKey(), e.getValue()))
        .collect(toList());

    val notFoundIds = Sets.difference(ids, jobProgresses.keySet());
    val notFoundIdsUiResponse = notFoundIds.stream()
        .map(id -> ImmutableMap.<String, Object> of("downloadId", id, "status", NOT_FOUND_STATUS))
        .collect(toList());

    jobStatuses.addAll(notFoundIdsUiResponse);

    return jobStatuses;
  }

  @GET
  @Timed
  @Path("/size")
  @Produces(APPLICATION_JSON)
  @ApiOperation("Get download size by type subject to the supplied filter condition(s)")
  public Map<Object, Object> getDataTypeSizePerFileType(
      @Auth(required = false) User user,
      @ApiParam(value = "Filter the search donors") @QueryParam("filters") @DefaultValue("{}") FiltersParam filters
      ) {
    // Work out the query for that returns only donor ids that matches the filter conditions
    val donorIds = donorService.findIds(Query.builder().filters(filters.get()).build());
    val dataTypeSizes = downloadClient.getSizes(donorIds);
    val allowedDataTypes = resolveAllowedDataTypes(user);

    val allowedDataTypeSizes = allowedDataTypes.stream()
        .map(type -> immutableEntry(type.getId(), dataTypeSizes.get(type)))
        .filter(e -> e.getValue() != null)
        .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue()));

    val response = newArrayList();
    for (val entry : allowedDataTypeSizes.entrySet()) {
      val items = ImmutableMap.of(
          "label", entry.getKey(),
          "sizes", entry.getValue());
      response.add(items);
    }

    return singletonMap("fileSize", response);
  }

  @GET
  @Timed
  @Produces(APPLICATION_JSON)
  @Path("{downloadIds}/info")
  @ApiOperation("Get the job info based on IDs")
  public Map<String, Map<String, Object>> getDownloadInfo(
      @Auth(required = false) User user,
      // TODO: after merge with shane's branch, use pathparam to handle this
      @ApiParam(value = "id", required = false) @PathParam("downloadIds") @DefaultValue("") IdsParam downloadIds
      ) throws IOException {
    val ids = downloadIds.get();
    if (ids == null || ids.isEmpty()) {
      throw new NotFoundException("Malformed request. Missing download IDs", "download");
    }

    val jobsInfo = downloadClient.getJobsInfo(ImmutableSet.copyOf(ids));
    ensureAccessPermissions(user, jobsInfo);
    val response = jobsInfo.entrySet().stream()
        .collect(toMap(e -> e.getKey(), e -> createUiJobInfoResponse(e.getValue())));

    val notFoundIds = Sets.difference(ImmutableSet.<String> copyOf(ids), jobsInfo.keySet());
    val notFoundIdsUiResponse = notFoundIds.stream()
        .collect(toMap(id -> id, id -> ImmutableMap.<String, Object> of("downloadId", id, "status", NOT_FOUND_STATUS)));

    response.putAll(notFoundIdsUiResponse);

    return response;
  }

  @GET
  @Timed
  @Path("/info{dir:.*}")
  @Produces(APPLICATION_JSON)
  @ApiOperation("Get file info under the specified directory")
  public List<FileInfo> listDirectory(
      @Auth(required = false) User user,
      // TODO: queryparam like fn
      @ApiParam(value = "listing of the specified directory under the download relative directory", required = false) @PathParam("dir") String dir
      ) throws IOException {
    try {
      if (dir.trim().isEmpty()) dir = "/";
      return listInfo(new File(dir), isAuthorized(user));
    } catch (FileNotFoundException e) {
      throw new BadRequestException("Directory not found: " + dir);
    } finally {
      log.info("Request dir: {}", dir);
    }
  }

  @GET
  @Timed
  @Path("/readme{dir:.*}")
  @Produces(APPLICATION_JSON)
  @ApiOperation("Get readme under the specified directory")
  public String getReadMe(
      // TODO: queryparam like fn
      @ApiParam(value = "directory that contains the readme", required = false) @PathParam("dir") String dir
      ) throws IOException {
    try {
      if (dir.trim().isEmpty()) dir = "/";
      return "Directory for readme: " + dir + "\n===\n\n" + "PLACEHOLDER FOR README" + "\n\n" + "HEADER 1 \n---\n\n"
          + "some content";
    } finally {
      log.info("Request dir: {}", dir);
    }
  }

  @ApiOperation("Get archive based by type subject to the supplied filter condition(s)")
  @GET
  @Timed
  public Response getStaticArchive(
      @Auth(required = false) User user,
      @ApiParam(value = "filename to download", required = true) @QueryParam("fn") @DefaultValue("") String filePath,
      @HeaderParam(RANGE) String range
      ) throws IOException {
    if (filePath.trim().equals("")) {
      throw new BadRequestException("Missing argument fn");
    }

    ensureServiceRunning();
    val downloadFile = new File(filePath);
    val predicate = isAuthorized(user) ? new ControlledAccessPredicate(fs) : new OpenAccessPredicate(fs);

    testFilePermissions(downloadFile, predicate);

    val contentLength = fs.getSize(downloadFile);

    ResponseBuilder response = ok();
    if (range != null) {
      val rangeHeader = parseRange(range, contentLength);
      log.debug("Parsed range header: {}", rangeHeader);

      response.header(ACCEPT_RANGES, "bytes")
          .header(CONTENT_RANGE, rangeHeader);
    }

    val archiveStream = archiveStream(downloadFile, getFromByte(range));
    val filename = downloadFile.getName();

    return response
        .entity(archiveStream)
        .type(getFileMimeType(filename))
        .header(CONTENT_LENGTH, contentLength)
        .header(CONTENT_DISPOSITION,
            type("attachment")
                .fileName(filename)
                .creationDate(new Date())
                .build())
        .build();
  }

  @GET
  @Timed
  @Path("/{downloadId}")
  @ApiOperation("Get archive based by type subject to the supplied filter condition(s)")
  public Response getFullArchive(
      @Auth(required = false) User user,
      @PathParam("downloadId") String downloadId
      ) throws IOException {
    ensureServiceRunning();

    val jobsStatus = downloadClient.getJobsProgress(ImmutableSet.<String> of(downloadId));
    val jobProgress = jobsStatus.get(downloadId);

    ensureJobAvailableForDownload(downloadId, jobProgress);

    // Check if the job still running
    val tasksProgress = jobProgress.getTaskProgress();
    val isRunning = tasksProgress
        .values().stream()
        .anyMatch(progress -> !isCompleted(progress));
    if (isRunning) {
      throw new NotFoundException(downloadId, "download");
    }

    // Check if the user is trying to download permitted datatypes
    val allowedDataTypes = resolveAllowedDataTypes(user);
    val availableDataTypes = tasksProgress.keySet();
    if (allowedDataTypes.containsAll(availableDataTypes) == false) {
      log.error("Permission denied for download types that need access control: {}, download id: {}",
          availableDataTypes, downloadId);
      throw new NotFoundException(downloadId, "download");
    }

    val archiveStream = archiveStream(downloadId, ImmutableList.copyOf(availableDataTypes));
    val filename = fileName(FULL_ARCHIVE_EXTENSION);

    return ok()
        .entity(archiveStream)
        .type(getFileMimeType(filename))
        .header(CONTENT_DISPOSITION,
            type("attachment")
                .fileName(filename)
                .creationDate(new Date())
                .build())
        .build();
  }

  // TODO: refactor
  @GET
  @Timed
  @Path("/{downloadId}/{dataType}")
  @ApiOperation("Get archive based by type subject to the supplied filter condition(s)")
  public Response getIndividualTypeArchive(
      @Auth(required = false) User user,
      @PathParam("downloadId") String downloadId,
      @PathParam("dataType") final String dataType
      ) throws IOException {
    ensureServiceRunning();

    val jobsStatus = downloadClient.getJobsProgress(ImmutableSet.<String> of(downloadId));
    val jobProgress = jobsStatus.get(downloadId);

    ensureJobAvailableForDownload(downloadId, jobProgress);

    val allowedDataTypes = resolveAllowedDataTypes(user);
    val tasksProgress = jobProgress.getTaskProgress();
    val availableDataTypes = tasksProgress.keySet();

    val downloadableTypes = Sets.intersection(availableDataTypes, allowedDataTypes);
    val selectedTypes = downloadableTypes.stream()
        .filter(dt -> dt.getCanonicalName().equals(dataType))
        .collect(toImmutableSet());

    if (selectedTypes.isEmpty()) {
      log.error("[{}] Permission denied for download type '{}' that needs access control.", downloadId, dataType);
      throw new NotFoundException(downloadId, "download");
    } else if (selectedTypes.size() > 1) {
      log.error("[{}] Failed to resolve DownloadDataType for requested type '{}'. Resolved ones: {}",
          new Object[] { downloadId, dataType, selectedTypes });
      throw new NotFoundException(downloadId, "download");
    }

    val selectedType = Iterables.get(selectedTypes, 0);
    if (isCompleted(tasksProgress.get(selectedType)) == false) {
      log.error("[{}] Data type '{}' is not ready for download yet.", downloadId, selectedType);
      throw new NotFoundException(downloadId, "download");
    }

    val actualDownloadTypes = resolveActualDownloadType(selectedType, availableDataTypes);

    String extension = INDIVIDUAL_TYPE_ARCHIVE_EXTENSION;
    StreamingOutput archiveStream = null;
    if (actualDownloadTypes.size() == 1) {
      archiveStream = archiveStream(downloadId, actualDownloadTypes.get(0));
    } else {
      archiveStream = archiveStream(downloadId, actualDownloadTypes);
      extension = FULL_ARCHIVE_EXTENSION;
    }

    val filename = fileName(extension);
    return ok().entity(archiveStream).type(getFileMimeType(filename))
        .header(CONTENT_DISPOSITION,
            type("attachment")
                .fileName(filename)
                .creationDate(new Date())
                .build())
        .build();
  }

  private Set<String> resolveDonorIds(FiltersParam filters) {
    val donorIds = donorService.findIds(Query.builder().filters(filters.get()).build());
    if (donorIds.isEmpty()) {
      log.error("No donor ids found for filter: {}", filters);
      throw new NotFoundException("No donor found", "download");
    }
    log.info("Number of donors to be retrieved: {}", donorIds.size());

    return donorIds;
  }

  private void ensureAccessPermissions(User user, Map<String, org.icgc.dcc.download.core.model.JobInfo> jobsInfo) {
    val controlled = hasControlledData(jobsInfo);
    if (isPermissionDenied(user, controlled)) {
      throw new ForbiddenAccessException("Unauthorized access", "download");
    }
  }

  private Set<DownloadDataType> resolveAllowedDataTypes(User user) {
    return isAuthorized(user) ? DownloadDataTypes.CONTROLLED_DATA_TYPES : DownloadDataTypes.PUBLIC_DATA_TYPES;
  }

  private Set<DownloadDataType> resolveDownloadDataTypes(User user, String rawDownloadDataTypes) {
    val dataTypeNames = parseDownloadDataTypeNames(rawDownloadDataTypes);
    val authorized = isAuthorized(user);
    val requestedDataTypes = dataTypeNames.stream()
        .map(name -> DownloadDataType.from(name, authorized))
        .collect(toImmutableSet());

    return Sets.intersection(resolveAllowedDataTypes(user), requestedDataTypes);
  }

  private Map<String, Object> createUiJobInfoResponse(org.icgc.dcc.download.core.model.JobInfo jobInfo) {
    val response = ImmutableMap.<String, Object> builder()
        .put("filter", jobInfo.getFilter())
        .put("uiQueryStr", jobInfo.getUiQueryStr())
        .put("startTime", String.valueOf(jobInfo.getStartTime()))
        .put("hasEmail", String.valueOf(!jobInfo.getEmail().equals(defaultEmail)))
        .put(IS_CONTROLLED, String.valueOf(jobInfo.isControlled()))
        .put("status", FOUND_STATUS);

    if (jobInfo.getCompletionTime() != 0) {
      response.put("et", jobInfo.getCompletionTime());
    }

    if (jobInfo.getFileSize() != 0) {
      response.put("fileSize", jobInfo.getFileSize());
    }

    if (jobInfo.getTtl() != 0) {
      response.put("ttl", String.valueOf(jobInfo.getTtl()));
    }

    return response.build();
  }

  private void ensureServiceRunning() {
    if (!downloadClient.isServiceAvailable()) {
      throw new ServiceUnavailableException("Downloader is disabled");
    }
  }

  private void testFilePermissions(File downloadFile, Predicate<File> predicate) {
    try {
      val hasValidPermission = (fs.isFile(downloadFile) && predicate.apply(downloadFile));
      if (!hasValidPermission) {
        throw new ForbiddenAccessException(downloadFile.getAbsolutePath(), "download");
      }
    } catch (IOException e) {
      log.error("Permission Denied", e);
      throw new NotFoundException(downloadFile.getAbsolutePath(), "download");
    }
  }

  private static List<DownloadDataType> resolveActualDownloadType(DownloadDataType selectedType,
      Set<DownloadDataType> availableDataTypes) {
    return selectedType != DownloadDataType.DONOR ?
        singletonList(selectedType) :
        availableDataTypes.stream()
            .filter(dt -> DownloadDataType.CLINICAL.contains(dt))
            .collect(toImmutableList());
  }

  private void ensureJobAvailableForDownload(String downloadId,
      final JobProgress jobProgress) {
    if (jobProgress == null || jobProgress.getStatus().equals(org.icgc.dcc.download.core.model.JobStatus.EXPIRED)) {
      throw new NotFoundException(downloadId, "download");
    }
  }

  /*
   * See:
   * https://github.com/aruld/jersey-streaming/blob/master/src/main/java/com/aruld/jersey/streaming/MediaResource.java
   */
  private static String parseRange(String range, long length) {
    val ranges = range.split("=")[1].split("-");
    val from = getFromByte(range);

    long to = length - 1;

    if (ranges.length == 2) {
      to = parseInt(ranges[1]);
    }

    return String.format("bytes %d-%d/%d", from, to, length);
  }

  private static long getFromByte(String range) {
    if (range == null) {
      return 0;
    }

    val ranges = range.split("=")[1].split("-");
    return parseLong(ranges[0]);
  }

  private boolean isPermissionDenied(User user, boolean isControlled) {
    if (isControlled && !isAuthorized(user)) {
      return true;
    } else {
      return false;
    }
  }

  private static Map<String, Object> standardizeStatus(String id,
      org.icgc.dcc.download.core.model.JobProgress jobProgress) {
    if (jobProgress == null) {
      return ImmutableMap.of("downloadId", id, "status", NOT_FOUND_STATUS);
    }

    val downloadDataTypesProgress = ImmutableList.<Map<String, String>> builder();
    val tasksProgress = jobProgress.getTaskProgress();

    for (val progressEntry : tasksProgress.entrySet()) {
      val uiProgress = createUiProgress(progressEntry);
      downloadDataTypesProgress.add(uiProgress);
    }

    val uiStatus = jobProgress.getStatus().name();

    return ImmutableMap.of(
        "downloadId", id,
        "status", uiStatus,
        "progress", downloadDataTypesProgress.build());
  }

  private static Map<String, String> createUiProgress(Entry<DownloadDataType, TaskProgress> progressEntry) {
    val dataType = progressEntry.getKey();
    val taskProgress = progressEntry.getValue();

    return ImmutableMap.<String, String> builder()
        .put("dataType", dataType.getCanonicalName())
        .put("completed", String.valueOf(isCompleted(taskProgress)))
        .put("numerator", String.valueOf(taskProgress.getNumerator()))
        .put("denominator", String.valueOf(taskProgress.getDenominator()))
        .put("percentage", String.valueOf(getPercentage(taskProgress)))
        .build();
  }

  private static boolean isCompleted(TaskProgress taskProgress) {
    return getPercentage(taskProgress) >= 1.0;
  }

  private static double getPercentage(TaskProgress taskProgress) {
    val numerator = taskProgress.getNumerator();
    val denominator = taskProgress.getDenominator();

    return (numerator == 0.0 && denominator == 0.0) ? 0.0 : (double) numerator / denominator;
  }

  private StreamingOutput archiveStream(String downloadId, List<DownloadDataType> selectedDataTypes) {
    return new StreamingOutput() {

      @Override
      public void write(final OutputStream out) throws IOException, WebApplicationException {
        if (!downloadClient.streamArchiveInTarGz(out, downloadId, selectedDataTypes)) {
          throw new BadRequestException("Data not found for download id: " + downloadId);
        }
      }
    };
  }

  private StreamingOutput archiveStream(String downloadId, DownloadDataType dataType) {
    return new StreamingOutput() {

      @Override
      public void write(final OutputStream out) throws IOException, WebApplicationException {
        if (!downloadClient.streamArchiveInGz(out, downloadId, dataType)) {
          throw new BadRequestException("Data not found for download id: " + downloadId);
        }
      }
    };
  }

  private StreamingOutput archiveStream(final File relativePath, long from) {
    return new StreamingOutput() {

      @Override
      public void write(final OutputStream out) throws IOException, WebApplicationException {
        try {
          @Cleanup
          InputStream in = fs.createInputStream(relativePath, from);
          IOUtils.copy(in, out);
        } catch (Exception e) {
          log.warn("Exception thrown from Dynamic Download Resource.", e);
        }
      }
    };
  }

  private static String fileName(String extension) {
    return format("icgc-dataset-%s" + extension, currentTimeMillis());
  }

  private List<FileInfo> listInfo(File dir, boolean authenticated) throws IOException {
    List<FileInfo> info = newArrayList();

    for (File file : Iterables.filter(fs.listFiles(dir),
        authenticated ? new ControlledAccessPredicate(fs) : new OpenAccessPredicate(fs))) {
      String type = "d";
      long size = 0;
      long date = fs.getModificationTime(file);
      String name = file.getName();
      if (fs.isFile(file)) {
        size = fs.getSize(file);
        type = "f";
      }

      info.add(new FileInfo(FilenameUtils.concat(dir.getPath(), name), type, size, date));
    }

    Collections.sort(info, new Comparator<FileInfo>() {

      @Override
      public int compare(FileInfo thisInfo, FileInfo thatInfo) {
        return thisInfo.getName().compareTo(thatInfo.getName());
      }

    });
    return info;
  }

  private void ensureResoucesAccessPermissions(User user, Set<String> ids) {
    val jobsInfo = downloadClient.getJobsInfo(ids);
    ensureAccessPermissions(user, jobsInfo);
  }

  private boolean isAuthorized(User user) {
    return (env == Stage.DEVELOPMENT || user != null);
  }

  private String getFileMimeType(String filename) {

    String ext = FilenameUtils.getExtension(filename);
    String type = TEXT_PLAIN;
    if (ext.equals("gz")) {
      type = APPLICATION_GZIP;
    } else if (ext.equals("tar")) {
      type = APPLICATION_TAR;
    }
    return type;
  }

  private static boolean hasControlledData(Map<String, org.icgc.dcc.download.core.model.JobInfo> jobsInfo) {
    return jobsInfo.values().stream()
        .anyMatch(jobInfo -> jobInfo.isControlled());
  }

}