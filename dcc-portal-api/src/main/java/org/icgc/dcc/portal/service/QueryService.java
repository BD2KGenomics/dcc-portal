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

package org.icgc.dcc.portal.service;

import static org.elasticsearch.index.query.FilterBuilders.matchAllFilter;
import static org.elasticsearch.index.query.FilterBuilders.missingFilter;
import static org.elasticsearch.index.query.FilterBuilders.nestedFilter;
import static org.elasticsearch.index.query.FilterBuilders.numericRangeFilter;
import static org.elasticsearch.index.query.FilterBuilders.termFilter;
import static org.elasticsearch.index.query.FilterBuilders.termsFilter;
import static org.icgc.dcc.portal.model.IndexModel.API_ENTITY_LIST_ID_FIELD_NAME;
import static org.icgc.dcc.portal.model.IndexModel.FIELDS_MAPPING;
import static org.icgc.dcc.portal.model.IndexModel.GENE_SET_QUERY_ID_FIELDS;
import static org.icgc.dcc.portal.model.IndexModel.GENE_SET_QUERY_TYPE_FIELDS;
import static org.icgc.dcc.portal.model.IndexModel.IS;
import static org.icgc.dcc.portal.model.IndexModel.MAX_FACET_TERM_COUNT;
import static org.icgc.dcc.portal.model.IndexModel.MISSING;
import static org.icgc.dcc.portal.model.IndexModel.NOT;
import static org.icgc.dcc.portal.service.TermsLookupService.TermLookupType.DONOR_IDS;
import static org.icgc.dcc.portal.service.TermsLookupService.TermLookupType.GENE_IDS;
import static org.icgc.dcc.portal.service.TermsLookupService.TermLookupType.MUTATION_IDS;

import java.util.List;
import java.util.UUID;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.icgc.dcc.portal.model.ChromosomeLocation;
import org.icgc.dcc.portal.model.IndexModel.GeneSetType;
import org.icgc.dcc.portal.model.IndexModel.Kind;
import org.icgc.dcc.portal.model.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@SuppressWarnings("deprecation")
@Slf4j
public class QueryService {

  private final static ObjectMapper MAPPER = new ObjectMapper();

  private static List<String> locationFields = Lists.newArrayList("location", "transcript.gene.location",
      "gene.location", "ssm.consequence.gene.location", "ssm.location", "donor.ssm.location", "gene.ssm.location");

  public static FilterBuilder getFilters(ObjectNode filters, Kind kind) {
    if (filters.fieldNames().hasNext()) return buildFilters(filters, kind);
    return matchAllFilter();
  }

  public static FilterBuilder getFilters(ObjectNode filters, Kind kind, ImmutableMap<Kind, String> nestedMapping,
      ImmutableMap<Kind, String> prefixMapping) {
    if (filters.fieldNames().hasNext()) {
      return buildFilters(filters, kind, nestedMapping, prefixMapping);
    }
    return matchAllFilter();
  }

  public static String[] getFields(Query query, Kind kind) {
    val typeFieldsMap = FIELDS_MAPPING.get(kind);
    val result = Lists.<String> newArrayList();

    if (query.hasFields()) {
      for (val field : query.getFields()) {
        if (typeFieldsMap.containsKey(field)) {
          result.add(typeFieldsMap.get(field));
        }
      }
    } else {
      result.addAll(typeFieldsMap.values().asList());
    }
    clearInvalidFields(result, kind);

    return result.toArray(new String[result.size()]);
  }

  /**
   * Remove fields that are objects in ES. They must be retrieved from source
   */
  private static void clearInvalidFields(List<String> fields, Kind kind) {
    val typeFieldsMap = FIELDS_MAPPING.get(kind);

    switch (kind) {
    case GENE:
      fields.remove(typeFieldsMap.get("externalDbIds"));
      fields.remove(typeFieldsMap.get("pathways"));
      break;
    case PROJECT:
      fields.remove(typeFieldsMap.get("experimentalAnalysisPerformedDonorCounts"));
      fields.remove(typeFieldsMap.get("experimentalAnalysisPerformedSampleCounts"));
      break;
    case OCCURRENCE:
      fields.remove(typeFieldsMap.get("observation"));
      break;
    case GENE_SET:
      fields.remove(typeFieldsMap.get("hierarchy"));
      fields.remove(typeFieldsMap.get("inferredTree"));
      fields.remove(typeFieldsMap.get("synonyms"));
      fields.remove(typeFieldsMap.get("altIds"));
      break;
    }
  }

  public static List<TermsFacetBuilder> getFacets(Query query, Kind kind, ImmutableList<String> facets,
      ObjectNode filters, ImmutableMap<Kind, String> nestedMapping, ImmutableMap<Kind, String> prefixMapping) {
    val fs = Lists.<TermsFacetBuilder> newArrayList();
    if (query.hasInclude("facets")) {
      for (String facet : facets) {
        val tf = FacetBuilders.termsFacet(facet).field(FIELDS_MAPPING.get(kind).get(facet)).size(MAX_FACET_TERM_COUNT);

        if (filters.fieldNames().hasNext()) {
          val facetFilters = filters.deepCopy();
          if (facetFilters.has(kind.getId())) {
            facetFilters.with(kind.getId()).remove(facet);
          }

          if (nestedMapping != null) {
            tf.facetFilter(getFilters(facetFilters, kind, nestedMapping, prefixMapping));
          } else {
            tf.facetFilter(getFilters(facetFilters, kind));
          }
        }
        fs.add(tf);
      }
    }
    return fs;
  }

  public static FilterBuilder buildFilters(ObjectNode filters, Kind kind,
      ImmutableMap<Kind, String> nestedMapping, ImmutableMap<Kind, String> prefixMapping) {
    val qb = FilterBuilders.boolFilter();
    boolean matchAll = true;
    for (Kind k : Kind.values()) {
      if (hasFilter(filters, k)) {
        matchAll = false;
        val termFilters = buildTypeFilters(filters, k, prefixMapping);
        if (k.equals(kind) || !nestedMapping.containsKey(k)) qb.must(termFilters);
        else {
          qb.must(nestedFilter(nestedMapping.get(k), termFilters));
        }
      }
    }
    return matchAll ? matchAllFilter() : qb;
  }

  public static FilterBuilder buildFilters(ObjectNode filters, Kind kind) {
    if (hasFilter(filters, kind)) {
      return buildTypeFilters(filters, kind, null);
    } else {
      return matchAllFilter();
    }
  }

  public static BoolFilterBuilder buildGeneFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.GENE, prefixMapping);
  }

  public static BoolFilterBuilder buildDonorFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.DONOR, prefixMapping);
  }

  public static BoolFilterBuilder buildProjectFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.PROJECT, prefixMapping);
  }

  public static BoolFilterBuilder buildMutationFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.MUTATION, prefixMapping);
  }

  public static BoolFilterBuilder buildConsequenceFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.CONSEQUENCE, prefixMapping);
  }

  public static BoolFilterBuilder buildTranscriptFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.TRANSCRIPT, prefixMapping);
  }

  public static BoolFilterBuilder buildGeneSetFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    val resultFilter = FilterBuilders.boolFilter();
    val geneSetNode = filters.path(Kind.GENE_SET.getId());

    String prefix = "";
    if (prefixMapping.containsKey(Kind.GENE_SET)) {
      prefix = String.format("%s.", prefixMapping.get(Kind.GENE_SET));
    }

    for (val geneSetType : GeneSetType.values()) {
      val geneSetIdFilter = FilterBuilders.boolFilter();
      val geneSetTypeFilter = FilterBuilders.boolFilter();

      boolean hasIds = false;
      boolean hasType = false;

      // Get query/filter fields associated with gene type
      val idFieldName = GENE_SET_QUERY_ID_FIELDS.get(geneSetType.getType());
      val typeFieldName = GENE_SET_QUERY_TYPE_FIELDS.get(geneSetType.getType());

      log.debug("idFieldName {}", idFieldName);
      log.debug("type {}", geneSetType);

      // Handles is and is_not cases
      if (geneSetNode.has(idFieldName)) {
        for (String bool : Lists.newArrayList(IS, NOT)) {
          val boolNode = geneSetNode.path(idFieldName).path(bool);
          if (boolNode.isMissingNode() || !boolNode.isArray()) continue;

          hasIds = true;

          // 1) Add IS or NOT terms
          List<String> termList = Lists.newArrayList();
          for (val item : boolNode) {
            termList.add(item.asText());
          }

          // 2) Special cases pending on type
          if (geneSetType.equals(GeneSetType.GENE_SET_TYPE_GO)) {
            if (bool.equals(IS)) {
              geneSetIdFilter.should(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "cellular_component"),
                  termList));
              geneSetIdFilter.should(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "biological_process"),
                  termList));
              geneSetIdFilter.should(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "molecular_function"),
                  termList));
            } else {
              geneSetIdFilter.mustNot(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "cellular_component"),
                  termList));
              geneSetIdFilter.mustNot(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "biological_process"),
                  termList));
              geneSetIdFilter.mustNot(termsFilter(
                  prefix + String.format("%s.%s", geneSetType.getType(), "molecular_function"),
                  termList));
            }
          } else if (geneSetType.equals(GeneSetType.GENE_SET_TYPE_ALL)) {
            if (bool.equals(IS)) {
              geneSetIdFilter.should(termsFilter(prefix + GeneSetType.GENE_SET_TYPE_PATHWAY.getType(), termList));
              geneSetIdFilter.should(termsFilter(prefix + GeneSetType.GENE_SET_TYPE_CURATED.getType(), termList));
              geneSetIdFilter.should(termsFilter(prefix + String.format("%s.%s", "go_term", "cellular_component"),
                  termList));
              geneSetIdFilter.should(termsFilter(prefix + String.format("%s.%s", "go_term", "biological_process"),
                  termList));
              geneSetIdFilter.should(termsFilter(prefix + String.format("%s.%s", "go_term", "molecular_function"),
                  termList));
            } else {
              geneSetIdFilter.mustNot(termsFilter(prefix + GeneSetType.GENE_SET_TYPE_PATHWAY.getType(), termList));
              geneSetIdFilter.mustNot(termsFilter(prefix + GeneSetType.GENE_SET_TYPE_CURATED.getType(), termList));
              geneSetIdFilter.mustNot(termsFilter(prefix + String.format("%s.%s", "go_term", "cellular_component"),
                  termList));
              geneSetIdFilter.mustNot(termsFilter(prefix + String.format("%s.%s", "go_term", "biological_process"),
                  termList));
              geneSetIdFilter.mustNot(termsFilter(prefix + String.format("%s.%s", "go_term", "molecular_function"),
                  termList));
            }
          } else {
            val idFilter = termsFilter(prefix + geneSetType.getType(), termList);
            if (bool.equals(IS)) {
              geneSetIdFilter.must(idFilter);
            } else {
              geneSetIdFilter.mustNot(idFilter);
            }
          }
        }
      }

      // Deals with hasXXX cases
      if (geneSetNode.has(typeFieldName)) {
        val type = geneSetNode.path(typeFieldName).asBoolean();
        val typeFilter = FilterBuilders.existsFilter(prefix + geneSetType.getType());
        hasType = true;

        // Determine must or must not
        if (type == true) {
          geneSetTypeFilter.must(typeFilter);
        } else {
          geneSetTypeFilter.mustNot(typeFilter);
        }
      }

      // Handles special case "all" for intersecting gene sets
      if (geneSetNode.has(idFieldName) && !geneSetNode.path(idFieldName).path("all").isMissingNode()) {
        val allNode = geneSetNode.path(idFieldName).path("all");
        val allFilter = FilterBuilders.boolFilter();

        for (val value : allNode) {
          val valueStr = value.asText();
          val valueFilter = FilterBuilders.boolFilter();
          valueFilter.should(termFilter(prefix + GeneSetType.GENE_SET_TYPE_PATHWAY.getType(), valueStr));
          valueFilter.should(termFilter(prefix + GeneSetType.GENE_SET_TYPE_CURATED.getType(), valueStr));
          valueFilter.should(termFilter(prefix + String.format("%s.%s", "go_term", "cellular_component"), valueStr));
          valueFilter.should(termFilter(prefix + String.format("%s.%s", "go_term", "biological_process"), valueStr));
          valueFilter.should(termFilter(prefix + String.format("%s.%s", "go_term", "molecular_function"), valueStr));
          allFilter.must(valueFilter);
        }
        resultFilter.must(allFilter);
      }

      // Build overall filter a geneset type
      if (hasType && hasIds) {
        resultFilter.must(FilterBuilders.boolFilter().should(geneSetIdFilter).should(geneSetTypeFilter));
      } else if (hasIds) {
        resultFilter.must(FilterBuilders.boolFilter().must(geneSetIdFilter));
      } else if (hasType) {
        resultFilter.must(FilterBuilders.boolFilter().must(geneSetTypeFilter));
      }
    }

    return resultFilter;
  }

  public static BoolFilterBuilder buildEmbOccurrenceFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.EMB_OCCURRENCE, prefixMapping);
  }

  public static BoolFilterBuilder buildObservationFilters(ObjectNode filters, ImmutableMap<Kind, String> prefixMapping) {
    return buildTypeFilters(filters, Kind.OBSERVATION, prefixMapping);
  }

  public static BoolFilterBuilder buildTypeFilters(ObjectNode filters, Kind kind,
      ImmutableMap<Kind, String> prefixMapping) {

    val termFilters = FilterBuilders.boolFilter();
    val fields = filters.path(kind.getId()).fields();
    while (fields.hasNext()) {
      val facetField = fields.next();

      // Check that facet field is in Field Mapping
      val typeMapping = FIELDS_MAPPING.get(kind);
      if (typeMapping.containsKey(facetField.getKey())) {
        String fieldName = typeMapping.get(facetField.getKey());

        // boolean isGeneId = fieldName.equals("_gene_id");

        if (prefixMapping != null && prefixMapping.containsKey(kind)) {
          fieldName = String.format("%s.%s", prefixMapping.get(kind), fieldName);
        }

        // Check for IS / IS NOT
        JsonNode boolNode = facetField.getValue();
        for (String bool : Lists.newArrayList(IS, NOT)) {
          // Just a place holder that will be overridden with a more specific kind of filter
          FilterBuilder fb;
          if (boolNode.has(bool)) {
            if (boolNode.get(bool).isArray()) {
              val items = Lists.<String> newArrayList();
              // Converts from JsonNodes to List<String> for termsFilter
              for (val item : boolNode.get(bool)) {
                items.add(item.textValue());
              }
              if (locationFields.contains(fieldName)) {
                // Chromosome location
                fb = locationFilters(kind, items, typeMapping, prefixMapping);
              } else if (items.remove(MISSING)) {
                // Missing
                val bf = FilterBuilders.boolFilter();
                val mf = missingFilter(fieldName).existence(true).nullValue(false);
                bf.should(mf);
                if (!items.isEmpty()) {
                  bf.should(termsFilter(fieldName, items));
                }
                fb = bf;
              } else if (fieldName.endsWith(API_ENTITY_LIST_ID_FIELD_NAME) || facetField.getKey().equals("id")) {
                // This will get generated twice if both upload-gene-list and gene-id are present,
                // but that may be ok, it will be like saying: (a or b) and (a or b)

                // HACK: we need to "OR" together gene-id and upload-gene-list, since they all translate
                // to gene-ids in the end. Note we implicitly assume that gene-id and upload-gene-list are
                // either both "IS" or both "NOT", they cannot have different boolean clauses.
                val listFilters = FilterBuilders.boolFilter();

                if (filters.path(kind.getId()).path("id").path(bool).isMissingNode() == false) {
                  val idNode = filters.get(kind.getId()).get("id").get(bool);
                  val ids = Lists.<String> newArrayList();

                  for (val id : idNode) {
                    ids.add(id.asText());
                  }

                  String idFieldName = typeMapping.get("id");

                  if (prefixMapping != null && prefixMapping.containsKey(kind)) {
                    idFieldName = String.format("%s.%s", prefixMapping.get(kind), idFieldName);
                  }
                  listFilters.should(termsFilter(idFieldName, ids));
                }

                if (filters.path(kind.getId()).path(API_ENTITY_LIST_ID_FIELD_NAME).path(bool).isMissingNode() == false) {
                  val listNode = filters.get(kind.getId()).get(API_ENTITY_LIST_ID_FIELD_NAME).get(bool);
                  val listId = UUID.fromString(listNode.get(0).asText());

                  // String listFieldName = "_gene_id"; // Because genelist is geneId
                  // String listFieldName = IndexModel.ENTITY_LIST_TYPE_MAPPING.get(kind);
                  String listFieldName = typeMapping.get("id");

                  if (prefixMapping != null && prefixMapping.containsKey(kind)) {
                    listFieldName = String.format("%s.%s", prefixMapping.get(kind), listFieldName);
                  }

                  if (kind.equals(Kind.GENE)) {
                    listFilters.should(TermsLookupService.createTermsLookupFilter(listFieldName, GENE_IDS, listId));
                  } else if (kind.equals(Kind.MUTATION)) {
                    listFilters.should(TermsLookupService.createTermsLookupFilter(listFieldName, MUTATION_IDS, listId));
                  } else if (kind.equals(Kind.DONOR)) {
                    listFilters.should(TermsLookupService.createTermsLookupFilter(listFieldName, DONOR_IDS, listId));
                  }

                }
                fb = listFilters;
              } else {
                // Catch all
                fb = termsFilter(fieldName, items);
              }
            } else {
              String value = boolNode.get(bool).textValue();
              if (locationFields.contains(fieldName)) {
                fb = locationFilter(kind, value, typeMapping, prefixMapping);
              } else if (value.equals(MISSING)) {
                fb = missingFilter(fieldName);
              } else {
                fb = termFilter(fieldName, value);
              }
            }

            // TODO: termFilters must exist / fb
            // {gene: {type:{is:[], exists:true}}}

            if (bool.equals(IS)) {
              termFilters.must(fb);
            } else if (bool.equals(NOT)) {
              termFilters.mustNot(fb);
            }
          }
        }

      }
    }
    return termFilters;
  }

  public static FilterBuilder locationFilters(Kind kind, List<String> locations,
      ImmutableMap<String, String> typeMapping, ImmutableMap<Kind, String> prefixMapping) {
    val locationFilters = FilterBuilders.boolFilter();
    for (String location : locations) {
      locationFilters.should(locationFilter(kind, location, typeMapping, prefixMapping));
    }
    return locationFilters;
  }

  public static FilterBuilder locationFilter(Kind kind, String value, ImmutableMap<String, String> typeMapping,
      ImmutableMap<Kind, String> prefixMapping) {
    val locationFilter = FilterBuilders.boolFilter();

    val location = ChromosomeLocation.parse(value);

    // Nested fields
    String prefix = "";
    if (prefixMapping != null && prefixMapping.containsKey(kind)) {
      prefix = String.format("%s.", prefixMapping.get(kind));
    }

    // Constrain chromosome
    locationFilter.must(FilterBuilders.termFilter(prefix + typeMapping.get("chromosome"), location.getChromosome()));

    if (location.hasStart()) {
      locationFilter.must(numericRangeFilter(prefix + typeMapping.get("end")).gte(location.getStart()));
    }

    if (location.hasEnd()) {
      locationFilter.must(numericRangeFilter(prefix + typeMapping.get("start")).lte(location.getEnd()));
    }

    return locationFilter;
  }

  // NOTE: This changes the filter structure so the Filter Building logic doesn't have to change.
  // Moves donor: {projectId,primarySite} -> project: {id,primarySite} because mutation index structure doesn't nest
  // project under donor
  // Making the change here instead of globally because donor facets don't work probably with project filters and this
  // keeps the QueryService code from branching
  public static ObjectNode remapD2P(ObjectNode filters) {
    if (filters.has("donor")) {
      val donor = (ObjectNode) filters.get("donor");
      val project = MAPPER.createObjectNode();

      if (donor.has("primarySite")) {
        project.put("primarySite", donor.remove("primarySite"));
      }
      if (donor.has("projectId")) {
        project.put("id", donor.remove("projectId"));
      }
      if (project.fieldNames().hasNext()) {
        filters.put("project", project);
      }
      if (donor.fieldNames().hasNext()) {
        filters.replace("donor", donor);
      } else {
        filters.remove("donor");
      }
    }
    return filters;
  }

  // NOTE: This changes the filter structure so the Filter Building logic doesn't have to change.
  // Moves gene: {pathwayId} -> pathway: {id} because pathways are nested
  public static ObjectNode remapG2P(ObjectNode filters) {
    if (filters.has("gene")) {
      val gene = (ObjectNode) filters.get("gene");
      val geneSet = MAPPER.createObjectNode();

      val geneSetList =
          ImmutableList.<String> of("geneSetId", "pathwayId", "goTermId", "curatedSetId", "hasGoTerm", "hasPathway",
              "hasCuratedSet");

      for (val geneSetIdentifier : geneSetList) {
        if (gene.has(geneSetIdentifier)) {
          geneSet.put(geneSetIdentifier, gene.remove(geneSetIdentifier));
        }
      }

      if (geneSet.fieldNames().hasNext()) {
        filters.put("geneSet", geneSet);
      }
      if (gene.fieldNames().hasNext()) {
        filters.replace("gene", gene);
      } else {
        filters.remove("gene");
      }
    }

    return filters;
  }

  // NOTE: This changes the filter structure
  // Moves mutation: {consequenceType} -> consequence: {type} because consequence type needs custom query
  public static ObjectNode remapM2C(ObjectNode filters) {
    // Needed only if both mutation and gene filters found, otherwise consequence can be on its own.
    if (filters.has("mutation")) {
      val mutation = (ObjectNode) filters.get("mutation");
      val consequence = MAPPER.createObjectNode();

      if (mutation.has("consequenceType")) {
        consequence.put("type", mutation.remove("consequenceType"));
      }
      if (mutation.has("functionalImpact")) {
        consequence.put("functionalImpact", mutation.remove("functionalImpact"));
      }
      if (consequence.fieldNames().hasNext()) {
        filters.put("consequence", consequence);
      }
      if (mutation.fieldNames().hasNext()) {
        filters.replace("mutation", mutation);
      } else {
        filters.remove("mutation");
      }
    }

    return filters;
  }

  // NOTE: This changes the filter structure
  // Moves mutation: {platform, verificationStatus} -> observation: {platform, verificationStatus} because they need
  // custom query
  public static ObjectNode remapM2O(ObjectNode filters) {
    // Needed only if both mutation and donor filters found, otherwise they can be on their own.
    if (filters.has("mutation")) {
      val mutation = (ObjectNode) filters.get("mutation");
      val observation = MAPPER.createObjectNode();

      if (mutation.has("platform")) {
        observation.put("platform", mutation.remove("platform"));
      }
      if (mutation.has("verificationStatus")) {
        observation.put("verificationStatus", mutation.remove("verificationStatus"));
      }
      if (mutation.has("sequencingStrategy")) {
        observation.put("sequencingStrategy", mutation.remove("sequencingStrategy"));
      }

      if (observation.fieldNames().hasNext()) {
        filters.put("observation", observation);
      }
      if (mutation.fieldNames().hasNext()) {
        filters.replace("mutation", mutation);
      } else {
        filters.remove("mutation");
      }
    }
    return filters;
  }

  static public final Boolean hasFilter(ObjectNode filters, Kind kind) {
    return filters.has(kind.getId()) && filters.path(kind.getId()).fieldNames().hasNext();
  }

  static public final Boolean hasDonor(ObjectNode filters) {
    return hasFilter(filters, Kind.DONOR);
  }

  static public final Boolean hasProject(ObjectNode filters) {
    return hasFilter(filters, Kind.PROJECT);
  }

  static public final Boolean hasOccurrence(ObjectNode filters) {
    return hasFilter(filters, Kind.EMB_OCCURRENCE);
  }

  static public final Boolean hasGene(ObjectNode filters) {
    return hasFilter(filters, Kind.GENE);
  }

  static public final Boolean hasMutation(ObjectNode filters) {
    return hasFilter(filters, Kind.MUTATION);
  }

  static public final Boolean hasConsequence(ObjectNode filters) {
    return hasFilter(filters, Kind.CONSEQUENCE);
  }

  static public final Boolean hasObservation(ObjectNode filters) {
    return hasFilter(filters, Kind.OBSERVATION);
  }

  static public final Boolean hasGeneSet(ObjectNode filters) {
    return hasFilter(filters, Kind.GENE_SET) ||
        (filters.has("pathway") && filters.path("pathway").fieldNames().hasNext());
  }

  static public final Boolean hasTranscript(ObjectNode filters) {
    return hasFilter(filters, Kind.TRANSCRIPT);
  }
}
