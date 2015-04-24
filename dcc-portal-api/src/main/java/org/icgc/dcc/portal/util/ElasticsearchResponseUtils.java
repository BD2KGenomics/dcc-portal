/*
 * Copyright (c) 2014 The Ontario Institute for Cancer Research. All rights reserved.                             
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

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static lombok.AccessLevel.PRIVATE;
import static org.icgc.dcc.portal.model.IndexModel.FIELDS_MAPPING;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import lombok.NoArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.icgc.dcc.portal.model.IndexModel.Kind;
import org.icgc.dcc.portal.model.Query;

import com.google.common.collect.Maps;

/**
 * Provides methods to retrieve values from SearchHit
 */
@Slf4j
@NoArgsConstructor(access = PRIVATE)
public final class ElasticsearchResponseUtils {

  /**
   * Returns first value of the list as a String
   */
  @SuppressWarnings("unchecked")
  public static String getString(Object values) {
    if (values == null) return null;

    if (values instanceof List<?>) {
      return ((List<String>) values).get(0);
    }

    if (values instanceof String) {
      return (String) values;
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  public static Long getLong(Object value) {
    if (value instanceof List) value = ((List<Object>) value).get(0);
    if (value instanceof Long) return (Long) value;
    if (value instanceof Float) return ((Float) value).longValue();
    if (value instanceof Integer) return (long) (Integer) value;

    return null;
  }

  public static Boolean getBoolean(Object values) {
    if (values == null) return null;

    if (values instanceof Boolean) {
      return (Boolean) values;
    }

    @SuppressWarnings("unchecked")
    val resultList = (List<Object>) values;

    return (Boolean) resultList.get(0);
  }

  private static void processConsequences(Map<String, Object> map, Query query) {
    if (query.hasInclude("consequences")) {
      log.debug("Copying transcripts to consequences...");
      map.put("consequences", map.get("transcript"));
      if (!query.hasInclude("transcripts")) {
        log.debug("Removing transcripts...");
        map.remove("transcript");
      }
    }
  }

  public static Map<String, Object> createMapFromSearchFields(Map<String, SearchHitField> fields) {
    val result = Maps.<String, Object> newHashMap();
    for (val field : fields.entrySet()) {
      result.put(field.getKey(), field.getValue().getValues());
    }

    return result;
  }

  public static Map<String, Object> createMapFromGetFields(Map<String, GetField> fields) {
    val result = Maps.<String, Object> newHashMap();
    for (val field : fields.entrySet()) {
      result.put(field.getKey(), field.getValue().getValues());
    }

    return result;
  }

  public static Map<String, Object> createResponseMap(GetResponse response, Query query, Kind kind) {
    val map = createMapFromGetFields(response.getFields());
    map.putAll(processSource(response.getSource(), query, kind));

    return map;
  }

  public static Map<String, Object> createResponseMap(SearchHit response, Query query, Kind kind) {
    val map = createMapFromSearchFields(response.getFields());
    map.putAll(processSource(response.getSource(), query, kind));

    return map;
  }

  public static void checkResponseState(String id, GetResponse response, Kind kind) {
    if (!response.isExists()) {
      val type = kind.getId().substring(0, 1).toUpperCase() + kind.getId().substring(1);
      log.info("{} {} not found.", type, id);

      val message = format("{\"code\": 404, \"message\":\"%s %s not found.\"}", type, id);
      throw new WebApplicationException(Response.status(NOT_FOUND).entity(message).build());
    }
  }

  private static Map<String, Object> processSource(Map<String, Object> source, Query query, Kind kind) {
    if (source == null) {
      return emptyMap();
    }

    val result = flatternMap(source, query, kind);
    processConsequences(result, query);

    return result;
  }

  public static Map<String, Object> flatternMap(Map<String, Object> source) {
    if (source == null) {
      return emptyMap();
    }

    return flatternMap(Optional.empty(), source, null, null);
  }

  public static Map<String, Object> flatternMap(Map<String, Object> source, Query query, Kind kind) {
    if (source == null) {
      return emptyMap();
    }

    return flatternMap(Optional.empty(), source, kind, query);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> flatternMap(Optional<String> prefix, Map<String, Object> source, Kind kind,
      Query query) {
    val result = Maps.<String, Object> newHashMap();

    for (val entry : source.entrySet()) {
      val fieldName = resolvePrefix(prefix, entry.getKey());
      if (entry.getValue() instanceof Map && !isSkip(fieldName, query, kind)) {
        result.putAll(flatternMap(Optional.of(entry.getKey()), (Map<String, Object>) entry.getValue(), kind, query));
      } else {
        result.put(fieldName, entry.getValue());
      }
    }

    return result;
  }

  /**
   * Some fields are maps and the client expects them to be a map. This methods resolves those maps and prevents them
   * from been 'flattern' further
   */
  private static boolean isSkip(String fieldName, Query query, Kind kind) {
    if (kind == null || query == null) {
      return false;
    }

    return (FIELDS_MAPPING.get(kind).containsValue(fieldName) || query.hasInclude(fieldName));
  }

  private static String resolvePrefix(Optional<String> prefix, String field) {
    return prefix.isPresent() ? format("%s.%s", prefix.get(), field) : field;
  }
}