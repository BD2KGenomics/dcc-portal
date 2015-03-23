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

package org.icgc.dcc.portal.repository;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.elasticsearch.action.search.SearchType.COUNT;
import static org.elasticsearch.action.search.SearchType.QUERY_THEN_FETCH;
import static org.elasticsearch.action.search.SearchType.SCAN;
import static org.elasticsearch.index.query.FilterBuilders.boolFilter;
import static org.elasticsearch.index.query.FilterBuilders.matchAllFilter;
import static org.elasticsearch.index.query.FilterBuilders.nestedFilter;
import static org.elasticsearch.index.query.QueryBuilders.customScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.icgc.dcc.portal.model.IndexModel.FIELDS_MAPPING;
import static org.icgc.dcc.portal.model.IndexModel.MAX_FACET_TERM_COUNT;
import static org.icgc.dcc.portal.service.QueryService.buildConsequenceFilters;
import static org.icgc.dcc.portal.service.QueryService.buildDonorFilters;
import static org.icgc.dcc.portal.service.QueryService.buildGeneFilters;
import static org.icgc.dcc.portal.service.QueryService.buildGeneSetFilters;
import static org.icgc.dcc.portal.service.QueryService.buildMutationFilters;
import static org.icgc.dcc.portal.service.QueryService.buildObservationFilters;
import static org.icgc.dcc.portal.service.QueryService.getFields;
import static org.icgc.dcc.portal.service.QueryService.hasConsequence;
import static org.icgc.dcc.portal.service.QueryService.hasDonor;
import static org.icgc.dcc.portal.service.QueryService.hasGene;
import static org.icgc.dcc.portal.service.QueryService.hasGeneSet;
import static org.icgc.dcc.portal.service.QueryService.hasMutation;
import static org.icgc.dcc.portal.service.QueryService.hasObservation;
import static org.icgc.dcc.portal.service.QueryService.remapG2P;
import static org.icgc.dcc.portal.service.QueryService.remapM2C;
import static org.icgc.dcc.portal.service.QueryService.remapM2O;
import static org.icgc.dcc.portal.service.TermsLookupService.createTermsLookupFilter;
import static org.icgc.dcc.portal.util.SearchResponses.hasHits;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.MultiSearchRequestBuilder;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet;
import org.elasticsearch.search.sort.SortOrder;
import org.icgc.dcc.portal.model.AndQuery;
import org.icgc.dcc.portal.model.IndexModel;
import org.icgc.dcc.portal.model.IndexModel.Kind;
import org.icgc.dcc.portal.model.IndexModel.Type;
import org.icgc.dcc.portal.model.PhenotypeResult;
import org.icgc.dcc.portal.model.Query;
import org.icgc.dcc.portal.model.Statistics;
import org.icgc.dcc.portal.model.TermFacet;
import org.icgc.dcc.portal.model.TermFacet.Term;
import org.icgc.dcc.portal.model.TermFacetForEntitySet;
import org.icgc.dcc.portal.service.TermsLookupService.TermLookupType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Slf4j
@Component
public class DonorRepository implements Repository {

  private static final String SCORE =
      "g = doc['gene._gene_id']; x = doc['gene._summary._ssm_count']; g.value == '' || g.empty || x.empty || x.value < 1 ? 0 : 1";

  private static final Type CENTRIC_TYPE = Type.DONOR_CENTRIC;
  private static final Type TYPE = Type.DONOR;
  private static final Kind KIND = Kind.DONOR;

  private static final ImmutableMap<Kind, String> NESTED_MAPPING = Maps.immutableEnumMap(ImmutableMap
      .<Kind, String> builder()
      .put(Kind.PROJECT, "project")
      .put(Kind.GENE, "gene")
      .put(Kind.MUTATION, "gene.ssm")
      .put(Kind.GENE_SET, "gene")
      .put(Kind.CONSEQUENCE, "gene.ssm.consequence")
      .put(Kind.OBSERVATION, "gene.ssm.observation")
      .build());

  static final ImmutableMap<Kind, String> PREFIX_MAPPING = NESTED_MAPPING;

  private static final ImmutableList<String> FACETS = ImmutableList.of("projectId", "primarySite", "gender",
      "tumourStageAtDiagnosis", "vitalStatus", "diseaseStatusLastFollowup", "relapseType", "ageAtDiagnosisGroup",
      "availableDataTypes", "analysisTypes", "projectName");

  private static final class PhenotypeFacetNames {

    private static final String AGE_AT_DIAGNOSIS = "ageAtDiagnosis";
    private static final String AVERAGE_AGE_AT_DIAGNOSIS = "Average" + AGE_AT_DIAGNOSIS;
    private static final String AGE_AT_DIAGNOSIS_GROUP = "ageAtDiagnosisGroup";
    private static final String GENDER = "gender";
    private static final String VITAL_STATUS = "vitalStatus";

  }

  private static final Optional<SimpleImmutableEntry<String, String>> EMPTY_PAIR = Optional.empty();

  private static Optional<SimpleImmutableEntry<String, String>> createPair(String first, String second) {
    return Optional.of(new SimpleImmutableEntry<String, String>(first, second));
  }

  private static boolean wantsStatistics(Optional<SimpleImmutableEntry<String, String>> value) {
    return value.isPresent();
  }

  /**
   * A lookup table of facet name (the key) and its corresponding stats facet name pair (the value) for phenotype
   * analyses. Set the value to EMPTY_PAIR to indicate no stats is needed for the main facet. *NOTE: Categorical terms
   * such as gender or vitalStatus can't have stats facets and, if you do that, your elasticsearch query will fail.
   */
  private static final ImmutableMap<String, Optional<SimpleImmutableEntry<String, String>>> FACETS_FOR_PHENOTYPE =
      ImmutableMap.of(
          PhenotypeFacetNames.GENDER, EMPTY_PAIR,
          PhenotypeFacetNames.VITAL_STATUS, EMPTY_PAIR,
          PhenotypeFacetNames.AGE_AT_DIAGNOSIS_GROUP, createPair(PhenotypeFacetNames.AVERAGE_AGE_AT_DIAGNOSIS,
              PhenotypeFacetNames.AGE_AT_DIAGNOSIS));

  private static final ImmutableMap<String, String> DONORS_FIELDS_MAPPING_FOR_PHENOTYPE =
      ImmutableMap.of(
          PhenotypeFacetNames.AGE_AT_DIAGNOSIS, "donor_age_at_diagnosis",
          PhenotypeFacetNames.AGE_AT_DIAGNOSIS_GROUP, "_summary._age_at_diagnosis_group",
          PhenotypeFacetNames.GENDER, "donor_sex",
          PhenotypeFacetNames.VITAL_STATUS, "donor_vital_status");

  @Getter(lazy = true, value = PRIVATE)
  private final ImmutableMap<String, ImmutableMap<String, Integer>> baselineTermsFacetsOfPhenotype =
      loadBaselineTermsFacetsOfPhenotype();

  private static final int SCAN_BATCH_SIZE = 1000;

  private final static TimeValue KEEP_ALIVE = new TimeValue(10000);

  private final Client client;
  private final String index;

  @Autowired
  DonorRepository(Client client, IndexModel indexModel) {
    this.index = indexModel.getIndex();
    this.client = client;
  }

  private List<TermsFacetBuilder> getFacets(Query query, ObjectNode filters) {
    val fs = Lists.<TermsFacetBuilder> newArrayList();

    if (query.hasInclude("facets")) {
      for (String facet : FACETS) {
        val tf = FacetBuilders.termsFacet(facet).field(FIELDS_MAPPING.get(KIND).get(facet)).size(MAX_FACET_TERM_COUNT);

        if (filters.fieldNames().hasNext()) {
          val facetFilters = filters.deepCopy();
          if (facetFilters.has(KIND.getId())) {
            facetFilters.with(KIND.getId()).remove(facet);
          }

          tf.facetFilter(getFilters(facetFilters));
        }
        fs.add(tf);
      }
    }
    return fs;
  }

  private FilterBuilder getFilters(ObjectNode filters) {
    if (filters.fieldNames().hasNext()) return buildFilters(filters);
    return matchAllFilter();
  }

  private FilterBuilder buildFilters(ObjectNode filters) {
    val qb = FilterBuilders.boolFilter();
    val musts = Lists.<FilterBuilder> newArrayList();

    boolean matchAll = true;
    boolean hasDonor = hasDonor(filters);
    boolean hasGene = hasGene(filters);
    boolean hasGeneSet = hasGeneSet(filters);
    boolean hasMutation = hasMutation(filters);
    boolean hasConsequence = hasConsequence(filters);
    boolean hasObservation = hasObservation(filters);

    if (hasDonor || hasGene || hasGeneSet || hasMutation || hasConsequence || hasObservation) {
      matchAll = false;
      if (hasDonor) {
        musts.add(buildDonorFilters(filters, PREFIX_MAPPING));
      }
      if (hasGene || hasGeneSet || hasMutation || hasConsequence || hasObservation) {
        val gb = FilterBuilders.boolFilter();
        val gMusts = buildGeneNestedFilters(filters, hasGene, hasGeneSet, hasMutation, hasConsequence, hasObservation);
        gb.must(gMusts.toArray(new FilterBuilder[gMusts.size()]));
        musts.add(nestedFilter(NESTED_MAPPING.get(Kind.GENE), gb));
      }
      qb.must(musts.toArray(new FilterBuilder[musts.size()]));
    }
    return matchAll ? matchAllFilter() : qb;
  }

  private List<FilterBuilder> buildGeneNestedFilters(ObjectNode filters, boolean hasGene, boolean hasGeneSet,
      boolean hasMutation, boolean hasConsequence, boolean hasObservation) {
    val gMusts = Lists.<FilterBuilder> newArrayList();
    if (hasGene) gMusts.add(buildGeneFilters(filters, PREFIX_MAPPING));
    if (hasGeneSet) gMusts.add(buildGeneSetFilters(filters, PREFIX_MAPPING));
    /*
     * if (hasGeneSet) { gMusts.add(nestedFilter(NESTED_MAPPING.get(Kind.GENE_SET), buildGeneSetFilters(filters,
     * PREFIX_MAPPING))); }
     */

    if (hasMutation || hasConsequence || hasObservation) {
      val nb = FilterBuilders.boolFilter();
      val nMusts = Lists.<FilterBuilder> newArrayList();
      if (hasMutation) nMusts.add(buildMutationFilters(filters, PREFIX_MAPPING));
      if (hasConsequence) nMusts.add(nestedFilter(NESTED_MAPPING.get(Kind.CONSEQUENCE),
          buildConsequenceFilters(filters, PREFIX_MAPPING)));

      if (hasObservation) {
        nMusts.add(nestedFilter(NESTED_MAPPING.get(Kind.OBSERVATION),
            buildObservationFilters(filters, PREFIX_MAPPING)));
      }
      nb.must(nMusts.toArray(new FilterBuilder[nMusts.size()]));
      gMusts.add(nestedFilter(NESTED_MAPPING.get(Kind.MUTATION), nb));
    }
    return gMusts;
  }

  @Override
  public SearchResponse findAllCentric(Query query) {
    val search = buildFindAllRequest(query, CENTRIC_TYPE);

    search.setQuery(buildQuery(query));
    log.debug("{}", search);
    SearchResponse response = search.execute().actionGet();
    log.debug("{}", response);

    return response;
  }

  public List<PhenotypeResult> getPhenotypeAnalysisResult(@NonNull final Collection<UUID> entitySetIds) {
    val baseline = getBaselineTermsFacetsOfPhenotype();

    // Here we eliminate duplicates and impose ordering (needed for reading the response items).
    val setIds = ImmutableSet.copyOf(entitySetIds).asList();

    val multiResponse = performMultiSearchForPhenotypeAnalysis(setIds);
    val responseItems = multiResponse.getResponses();
    val responseItemCount = responseItems.length;
    checkState(responseItemCount == setIds.size(),
        "The number of queries does not match the number of responses in a multi-search.");

    val resultBuilders = FACETS_FOR_PHENOTYPE.keySet().stream()
        .collect(Collectors.toMap(name -> name, name -> new ImmutableList.Builder<TermFacetForEntitySet>()));

    for (int i = 0; i < responseItemCount; i++) {
      val facets = responseItems[i].getResponse().getFacets();

      if (null == facets) continue;

      val facetMap = facets.facetsAsMap();
      val entitySetId = setIds.get(i);
      val keyValues = FACETS_FOR_PHENOTYPE.entrySet();

      for (val facetKv : keyValues) {
        val facetName = facetKv.getKey();
        val facet = facetMap.get(facetName);

        if (facet instanceof TermsFacet) {
          val termsFacet = (TermsFacet) facet;
          val termFacetList = buildTermFacetList(termsFacet, baseline);

          val maybe = facetKv.getValue();
          val mean = wantsStatistics(maybe) ? getMeanFromTermsStatsFacet(facetMap.get(maybe.get().getKey())) : null;
          val summary = new Statistics(termsFacet.getTotalCount(), termsFacet.getMissingCount(), mean);

          resultBuilders.get(facetName)
              .add(new TermFacetForEntitySet(entitySetId, termFacetList, summary));
        }

      }

    }

    val result = resultBuilders.entrySet().stream()
        .map(entry -> new PhenotypeResult(entry.getKey(), entry.getValue().build()))
        .collect(Collectors.toList());

    return ImmutableList.copyOf(result);
  }

  private ImmutableMap<String, ImmutableMap<String, Integer>> loadBaselineTermsFacetsOfPhenotype() {
    val search = getSearchBuilderForPhenotypeAnalysis();
    val response = search.execute().actionGet();

    log.debug("ES query is: '{}'", search);
    log.debug("ES response is: '{}'", response);

    val builder = ImmutableMap.<String, ImmutableMap<String, Integer>> builder();

    val facets = response.getFacets();
    log.debug("facetGroups are: '{}'", facets);

    if (null == facets) {
      val message = "Query response does not contain any facets.";
      log.error(message);
      throw new RuntimeException(message);
    }

    facets.facets().stream()
        .filter(facet -> facet instanceof TermsFacet)
        .forEach(facet -> {
          val termFacet = TermFacet.of((TermsFacet) facet);
          val terms = termFacet.getTerms();

          if (isNotEmpty(terms)) {
            val innerBuilder = ImmutableMap.<String, Integer> builder();
            terms.forEach(term -> innerBuilder.put(term.getTerm(), 0));

            builder.put(facet.getName(), innerBuilder.build());
          }

        });

    return builder.build();
  }

  private static ImmutableList<Term> buildTermFacetList(@NonNull final TermsFacet termsFacet,
      @NonNull final ImmutableMap<String, ImmutableMap<String, Integer>> baseline) {
    val mapBuilder = ImmutableMap.<String, Integer> builder();
    TermFacet.of(termsFacet).getTerms()
        .forEach(term -> mapBuilder.put(term.getTerm(), term.getCount()));

    val facetName = termsFacet.getName();

    // Augmenting the result here in case of missing terms in the response.
    if (baseline.containsKey(facetName)) {
      val baselineMap = baseline.get(facetName);
      val difference = Maps.difference(mapBuilder.build(), baselineMap).entriesOnlyOnRight();
      mapBuilder.putAll(difference);
    }

    val termFacetList = mapBuilder.build().entrySet().stream()
        .map(m -> new Term(m.getKey(), m.getValue()))
        .collect(Collectors.toList());
    log.debug("termFacetList is: '{}'", termFacetList);

    return ImmutableList.copyOf(termFacetList);
  }

  private static Double getMeanFromTermsStatsFacet(final Facet facet) {
    if (!(facet instanceof TermsStatsFacet)) {
      return null;
    }

    val stats = ((TermsStatsFacet) facet).getEntries();

    return (stats.size() > 0) ? stats.get(0).getMean() : 0;
  }

  private MultiSearchResponse performMultiSearchForPhenotypeAnalysis(final List<UUID> setIds) {
    val multiSearch = client.prepareMultiSearch();
    val matchAll = matchAllQuery();

    for (val setId : setIds) {
      val search = getSearchBuilderForPhenotypeAnalysis();
      search.setQuery(filteredQuery(matchAll, getDonorSetIdFilterBuilder(setId)));

      // Adding terms_stats facets
      FACETS_FOR_PHENOTYPE.values().stream()
          .filter(v -> wantsStatistics(v))
          .forEach(optional -> {
            val kv = optional.get();
            val statsFacetName = kv.getKey();
            val statsFacetField = kv.getValue();

            val statsBuilder = FacetBuilders.termsStatsFacet(statsFacetName)
                .keyField("_type")
                .valueField(DONORS_FIELDS_MAPPING_FOR_PHENOTYPE.get(statsFacetField))
                .size(MAX_FACET_TERM_COUNT);
            search.addFacet(statsBuilder);
          });

      log.info("Sub-search for DonorSet ID [{}] is: '{}'", setId, search);
      multiSearch.add(search);
    }

    val multiResponse = multiSearch.execute().actionGet();
    log.info("MultiResponse is: '{}'.", multiResponse);

    return multiResponse;
  }

  private SearchRequestBuilder getSearchBuilderForPhenotypeAnalysis() {
    val typeId = Type.DONOR_CENTRIC.getId();

    val fieldMap = DONORS_FIELDS_MAPPING_FOR_PHENOTYPE;
    final String[] fields = fieldMap.values().stream().toArray(String[]::new);;

    val searchBuilder = client.prepareSearch(index)
        .setTypes(typeId)
        .setSearchType(QUERY_THEN_FETCH)
        .setFrom(0)
        .setSize(0)
        .addFields(fields);

    FACETS_FOR_PHENOTYPE.keySet().stream().map(name ->
        FacetBuilders.termsFacet(name)
            .field(fieldMap.get(name))
            .size(MAX_FACET_TERM_COUNT)).forEach(facetBuilder ->
        searchBuilder.addFacet(facetBuilder));

    return searchBuilder;
  }

  private static FilterBuilder getDonorSetIdFilterBuilder(final UUID donorId) {
    if (null == donorId) {
      return matchAllFilter();
    }

    val mustFilterFieldName = "_id";
    // TODO: should not reference TermsLookupService here
    val termsLookupFilter = createTermsLookupFilter(mustFilterFieldName, TermLookupType.DONOR_IDS, donorId);

    return boolFilter().must(termsLookupFilter);
  }

  @Override
  public SearchResponse findAll(Query query) {
    val search = buildFindAllRequest(query, TYPE);

    log.debug("{}", search);
    SearchResponse response = search.execute().actionGet();
    log.debug("{}", response);

    return response;
  }

  @Override
  public SearchRequestBuilder buildFindAllRequest(Query query, Type type) {
    val search =
        client.prepareSearch(index).setTypes(type.getId()).setSearchType(QUERY_THEN_FETCH).setFrom(query.getFrom())
            .setSize(query.getSize());

    ObjectNode filters = remapFilters(query.getFilters());
    search.setFilter(getFilters(filters));

    search.addFields(getFields(query, KIND));

    if (query.hasInclude("specimen")) search.addFields("specimen");

    val facets = getFacets(query, filters);
    for (val facet : facets) {
      search.addFacet(facet);
    }

    String sort = cleanSort(query.getSort());

    search.addSort(fieldSort(sort).order(query.getOrder()));
    if (!sort.equals("_score")) search.addSort("_score", SortOrder.DESC);

    return search;
  }

  private String cleanSort(String sort) {
    // Need to remove 'project.' from the sort field for some reason
    return FIELDS_MAPPING.get(KIND).get(sort).replace("project.", "");
  }

  protected FilterBuilder buildScoreFilters(Query query) {
    ObjectNode filters = remapFilters(query.getFilters());

    val qb = FilterBuilders.boolFilter();
    boolean matchAll = true;

    boolean hasGene = hasGene(filters);
    boolean hasGeneSet = hasGeneSet(filters);
    boolean hasMutation = hasMutation(filters);
    boolean hasConsequence = hasConsequence(filters);
    boolean hasObservation = hasObservation(filters);

    if (hasGene || hasGeneSet || hasMutation || hasConsequence || hasObservation) {
      matchAll = false;
      val gMusts = buildGeneNestedFilters(filters, hasGene, hasGeneSet, hasMutation, hasConsequence, hasObservation);
      qb.must(gMusts.toArray(new FilterBuilder[gMusts.size()]));
    }

    return matchAll ? matchAllFilter() : qb;
  }

  @Override
  public long count(Query query) {
    val search = buildCountRequest(query, CENTRIC_TYPE);

    log.debug("{}", search);

    return search.execute().actionGet().getHits().getTotalHits();
  }

  public long countIntersection(AndQuery query) {
    val search = client.prepareSearch(index).setTypes(CENTRIC_TYPE.getId()).setSearchType(COUNT);

    if (query.hasFilters()) {
      // Require all filter components to be true
      val boolFilter = new BoolFilterBuilder();
      for (val filters : query.getAndFilters()) {
        val remappedFilters = remapFilters(filters);

        boolFilter.must(getFilters(remappedFilters));
      }

      search.setFilter(boolFilter);
    }

    log.debug("{}", search);

    return search.execute().actionGet().getHits().getTotalHits();
  }

  @Override
  public MultiSearchResponse counts(LinkedHashMap<String, Query> queries) {
    MultiSearchRequestBuilder search = client.prepareMultiSearch();

    for (val id : queries.keySet()) {
      search.add(buildCountRequest(queries.get(id), CENTRIC_TYPE));
    }

    log.debug("{}", search);

    return search.execute().actionGet();
  }

  @Override
  public MultiSearchResponse nestedCounts(LinkedHashMap<String, LinkedHashMap<String, Query>> queries) {
    MultiSearchRequestBuilder search = client.prepareMultiSearch();

    for (val id1 : queries.keySet()) {
      val nestedQuery = queries.get(id1);
      for (val id2 : nestedQuery.keySet()) {
        search.add(buildCountRequest(nestedQuery.get(id2), CENTRIC_TYPE));
      }
    }

    log.debug("{}", search);

    return search.execute().actionGet();
  }

  public SearchRequestBuilder buildCountRequest(Query query, Type type) {
    val search = client.prepareSearch(index).setTypes(type.getId()).setSearchType(COUNT);

    if (query.hasFilters()) {
      ObjectNode filters = remapFilters(query.getFilters());
      search.setFilter(getFilters(filters));
      search.setQuery(buildQuery(query));
    }

    return search;
  }

  @Override
  public NestedQueryBuilder buildQuery(Query query) {
    return nestedQuery(
        "gene",
        customScoreQuery(filteredQuery(matchAllQuery(), buildScoreFilters(query))).script(SCORE)).scoreMode("total");
  }

  public ObjectNode remapFilters(ObjectNode filters) {
    return remapM2O(remapG2P(remapM2C(filters)));
  }

  public Map<String, Object> findOne(String id, Query query) {
    val fieldMapping = FIELDS_MAPPING.get(KIND);
    val fs = Lists.<String> newArrayList();

    GetRequestBuilder search = client.prepareGet(index, TYPE.getId(), id);

    if (query.hasFields()) {
      for (String field : query.getFields()) {
        if (fieldMapping.containsKey(field)) {
          fs.add(fieldMapping.get(field));
        }
      }
    } else
      fs.addAll(fieldMapping.values().asList());

    if (query.hasInclude("specimen")) fs.add("specimen");

    search.setFields(fs.toArray(new String[fs.size()]));

    GetResponse response = search.execute().actionGet();

    if (!response.isExists()) {
      String type = KIND.getId().substring(0, 1).toUpperCase() + KIND.getId().substring(1);
      String msg = String.format("{\"code\": 404, \"message\":\"%s %s not found.\"}", type, id);
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
          .entity(msg).build());
    }

    val map = Maps.<String, Object> newHashMap();

    for (GetField f : response.getFields().values()) {
      if (Lists.newArrayList(fieldMapping.get("availableDataTypes"), fieldMapping.get("analysisTypes"), "specimen")
          .contains(f.getName())) {
        map.put(f.getName(), f.getValues());
      } else {
        map.put(f.getName(), f.getValue());
      }
    }

    log.debug("{}", map);

    return map;
  }

  public Set<String> findIds(Query query) {
    // TODO: Now assume 5000 ids at least
    Set<String> donorIds = newHashSetWithExpectedSize(5000);
    ObjectNode filters = remapFilters(query.getFilters());

    SearchRequestBuilder search = client
        .prepareSearch(index)
        .setTypes(CENTRIC_TYPE.getId())
        .setSearchType(SCAN)
        .setSize(SCAN_BATCH_SIZE)
        .setScroll(KEEP_ALIVE)
        .setFilter(getFilters(filters))
        .setQuery(matchAllQuery())
        .setNoFields();

    SearchResponse response = search.execute().actionGet();
    while (true) {
      response = client.prepareSearchScroll(response.getScrollId())
          .setScroll(KEEP_ALIVE)
          .execute().actionGet();

      for (SearchHit hit : response.getHits()) {
        donorIds.add(hit.getId());
      }

      val finished = !hasHits(response);
      if (finished) {
        break;
      }
    }

    return donorIds;
  }
}
