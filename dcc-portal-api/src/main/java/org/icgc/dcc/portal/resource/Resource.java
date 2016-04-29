/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.portal.resource;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.icgc.dcc.common.core.util.Joiners.COMMA;
import static org.icgc.dcc.portal.util.ListUtils.mapList;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.validation.Validation;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.icgc.dcc.portal.model.FiltersParam;
import org.icgc.dcc.portal.model.Query;
import org.icgc.dcc.portal.model.Query.QueryBuilder;
import org.icgc.dcc.portal.resource.entity.MutationResource;
import org.icgc.dcc.portal.service.BadRequestException;
import org.icgc.dcc.portal.util.JsonUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.jersey.params.IntParam;

import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * Base classes for all API resources.
 */
@Slf4j
public abstract class Resource {

  /**
   * Default constants.
   */
  protected static final String DEFAULT_FIELDS = "";
  protected static final String DEFAULT_FILTERS = "{}";
  protected static final String DEFAULT_FACETS = "true";
  protected static final String DEFAULT_SIZE = "10";
  protected static final String DEFAULT_FROM = "1";
  protected static final String DEFAULT_SORT = "_score";
  protected static final String DEFAULT_ORDER = "desc";
  protected static final String DEFAULT_MIN_SCORE = "0";

  protected static final String DEFAULT_PROJECT_SORT = "totalLiveDonorCount";
  protected static final String DEFAULT_OCCURRENCE_SORT = "donorId";
  protected static final String DEFAULT_DONOR_SORT = "ssmAffectedGenes";
  protected static final String DEFAULT_GENE_MUTATION_SORT = "affectedDonorCountFiltered";

  /**
   * Logging template constants.
   */
  protected static final String COUNT_TEMPLATE = "Request for a count of {} with filters '{}'";
  protected static final String FIND_ALL_TEMPLATE =
      "Request for '{}' {} from index '{}', sorted by '{}' in '{}' order with filters '{}'";
  protected static final String FIND_ONE_TEMPLATE = "Request for '{}'";
  protected static final String NESTED_FIND_TEMPLATE = "Request {} for '{}'";
  protected static final String NESTED_COUNT_TEMPLATE = "Request {} count for '{}'";
  protected static final String NESTED_NESTED_COUNT_TEMPLATE = "Request count of '{}' in '{}' affected by '{}'";

  /**
   * Other constants.
   */
  private static final Joiner COMMA_JOINER = COMMA.skipNulls();
  private static final List<String> EMPTY_VALUES = newArrayList("", null);

  /**
   * JAX-RS uri information for building mutation links.
   */
  @Context
  protected UriInfo uriInfo;

  /**
   * Creates a mutation URL from the supplied mutation id.
   * 
   * @param mutationId - the mutation id
   * @return the mutation URL
   */
  protected URI mutationUrl(String mutationId) {
    return uriInfo
        .getBaseUriBuilder()
        .path(MutationResource.class)
        .path(mutationId)
        .build();
  }

  /**
   * Returns the total matching count independent of paging.
   * 
   * @param response - the search results
   * @return the total matching count
   */
  protected static long count(SearchResponse response) {
    return response.getHits().totalHits();
  }

  /**
   * Utility method.
   * 
   * @param hit
   * @param fieldName
   * @return
   */
  protected static Object field(SearchHit hit, String fieldName) {
    return hit.field(fieldName).value();
  }

  /**
   * Utility method to access a search hit partial field in a type safe manner.
   * 
   * @param hit - the search hit
   * @param fieldName - the partial field name
   * @return
   */
  protected static List<Map<String, Object>> partialField(SearchHit hit, String fieldName) {
    SearchHitField field = hit.field(fieldName);
    if (field == null) {
      return null;
    }

    Map<String, Object> value = field.value();

    return mapList(value.get(fieldName));
  }

  /**
   * Readability methods for map building.
   * 
   * @return the map builder
   */
  protected static Builder<String, Object> response() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static Builder<String, Object> hit() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static Builder<String, Object> record() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static Builder<String, Object> mutation() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static Builder<String, Object> consequence() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static Builder<String, Object> fields() {
    return ImmutableMap.<String, Object> builder();
  }

  protected static LinkedHashMap<String, Query> generateQueries(ObjectNode filters, String filterTemplate,
      List<String> ids) {
    val queries = Maps.<String, Query> newLinkedHashMap();

    for (String id : ids) {
      val filter = mergeFilters(filters, filterTemplate, id);
      queries.put(id, query().filters(filter).build());
    }
    return queries;
  }

  protected static LinkedHashMap<String, Query> generateQueries(ObjectNode filters, String filterTemplate,
      List<String> ids,
      String anchorId) {
    val queries = Maps.<String, Query> newLinkedHashMap();

    for (String id : ids) {
      val filter = mergeFilters(filters, filterTemplate, id, anchorId);
      queries.put(id, query().filters(filter).build());
    }
    return queries;
  }

  protected static LinkedHashMap<String, LinkedHashMap<String, Query>> generateQueries(ObjectNode filters,
      String filterTemplate,
      List<String> ids,
      List<String> anchorIds) {
    val queries = Maps.<String, LinkedHashMap<String, Query>> newLinkedHashMap();

    for (String anchorId : anchorIds) {
      queries.put(anchorId, generateQueries(filters, filterTemplate, ids, anchorId));
    }
    return queries;
  }

  protected static ObjectNode mergeFilters(ObjectNode filters, String template, Object... objects) {
    return JsonUtils.merge(filters, (new FiltersParam(String.format(template, objects)).get()));
  }

  protected static QueryBuilder query() {
    return Query.builder();
  }

  /**
   * @see http://stackoverflow.com/questions/23704616/how-to-validate-a-single-parameter-in-dropwizard
   */
  protected static void validate(@NonNull Object object) {
    val errorMessages = new ArrayList<String>();
    val validator = Validation.buildDefaultValidatorFactory().getValidator();

    val violations = validator.validate(object);
    if (!violations.isEmpty()) {
      for (val violation : violations) {
        errorMessages.add("'" + violation.getPropertyPath() + "' " + violation.getMessage());
      }

      throw new BadRequestException(COMMA_JOINER.join(errorMessages));
    }

  }

  protected static void checkRequest(boolean errorCondition, String formatTemplate, Object... args) {
    if (errorCondition) {
      // We don't want exception within an exception-handling routine.
      final Supplier<String> errorMessageProvider = () -> {
        try {
          return format(formatTemplate, args);
        } catch (Exception e) {
          final String errorDetails = "message: '" + formatTemplate +
              "', parameters: '" + COMMA_JOINER.join(args) + "'";
          log.error("Error while formatting message - " + errorDetails, e);

          return "Invalid web request - " + errorDetails;
        }
      };

      throw new BadRequestException(errorMessageProvider.get());
    }
  }

  protected static List<String> removeNullAndEmptyString(List<String> source) {
    if (isEmpty(source)) {
      return source;
    }

    source.removeAll(EMPTY_VALUES);

    return source;
  }

  protected static Query regularFindAllJqlQuery(List<String> fields, List<String> include, ObjectNode filters,
      IntParam from, IntParam size, String sort, String order) {
    val query = query()
        .fields(fields).filters(filters)
        .from(from.get()).size(size.get())
        .sort(sort).order(order);

    removeNullAndEmptyString(include);
    if (!include.isEmpty()) {
      query.includes(include);
    }

    return query.build();
  }

}
