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
package org.icgc.dcc.portal.enrichment;

import static com.google.common.base.Stopwatch.createStarted;
import static org.icgc.dcc.portal.enrichment.EnrichmentAnalyses.adjustRawGeneSetResults;
import static org.icgc.dcc.portal.enrichment.EnrichmentAnalyses.calculateExpectedValue;
import static org.icgc.dcc.portal.enrichment.EnrichmentAnalyses.calculateHypergeometricTest;
import static org.icgc.dcc.portal.enrichment.EnrichmentQueries.geneSetOverlapQuery;
import static org.icgc.dcc.portal.enrichment.EnrichmentQueries.overlapQuery;
import static org.icgc.dcc.portal.enrichment.EnrichmentSearchResponses.getUniverseTermsFacet;
import static org.icgc.dcc.portal.model.EnrichmentAnalysis.State.FINISHED;
import static org.icgc.dcc.portal.model.Query.idField;
import static org.icgc.dcc.portal.service.TermsLookupService.TermLookupType.GENE_IDS;
import static org.icgc.dcc.portal.util.Facets.getFacetCounts;
import static org.icgc.dcc.portal.util.SearchResponses.getHitIds;

import java.util.List;
import java.util.UUID;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.portal.model.AndQuery;
import org.icgc.dcc.portal.model.EnrichmentAnalysis;
import org.icgc.dcc.portal.model.EnrichmentAnalysis.Overview;
import org.icgc.dcc.portal.model.EnrichmentAnalysis.Result;
import org.icgc.dcc.portal.model.Query;
import org.icgc.dcc.portal.model.Universe;
import org.icgc.dcc.portal.repository.DonorRepository;
import org.icgc.dcc.portal.repository.EnrichmentAnalysisRepository;
import org.icgc.dcc.portal.repository.GeneRepository;
import org.icgc.dcc.portal.repository.GeneSetRepository;
import org.icgc.dcc.portal.repository.MutationRepository;
import org.icgc.dcc.portal.service.TermsLookupService;
import org.icgc.dcc.portal.util.Facets.Count;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Technical specification for this feature may be found here:
 * 
 * https://wiki.oicr.on.ca/display/DCCSOFT/Data+Portal+-+Enrichment+Analysis+-+Technical+Specification
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @_(@Autowired))
public class EnrichmentAnalyzer {

  /**
   * Constants.
   */
  private static final String API_GENE_COUNT_FIELD_NAME = "geneCount";
  private static final String INDEX_GENE_COUNT_FIELD_NAME = "_summary._gene_count";
  private static final String INDEX_GENE_SETS_NAME_FIELD_NAME = "name";

  /**
   * Dependencies.
   */
  @NonNull
  private final TermsLookupService termLookupService;
  @NonNull
  private final EnrichmentAnalysisRepository analysisRepository;
  @NonNull
  private final GeneRepository geneRepository;
  @NonNull
  private final GeneSetRepository geneSetRepository;
  @NonNull
  private final DonorRepository donorRepository;
  @NonNull
  private final MutationRepository mutationRepository;

  /**
   * This method runs asynchronously to perform enrichment analysis.
   * 
   * @param analysis the definition
   */
  @Async
  public void analyze(@NonNull EnrichmentAnalysis analysis) {
    val watch = createStarted();
    log.info("Executing analysis for {}...", analysis);

    // Shorthands
    val query = analysis.getQuery();
    val params = analysis.getParams();
    val universe = params.getUniverse();
    val inputGeneListId = analysis.getId();

    // Determine "InputGeneList"
    log.info("Finding input gene list @ {}...", watch);
    val inputGeneList = findInputGeneList(query, params.getMaxGeneCount());

    // Save ids in index for efficient search using "term lookup"
    log.info("Indexing input gene list @ {}...", watch);
    indexInputGeneList(inputGeneListId, inputGeneList);

    // Get all gene-set gene counts of the input query
    log.info("Calculating gene set counts @ {}...", watch);
    val geneSetCounts = calculateGeneSetCounts(
        query,
        universe,
        inputGeneListId);

    // Overview section
    log.info("Calculating overview @ {}...", watch);
    val overview = calculateOverview(query, universe, inputGeneListId);

    // Perform gene-set specific calculations
    log.info("Calculating raw gene set results @ {}...", watch);
    val rawResults = calculateRawGeneSetsResults(
        query,
        universe,
        inputGeneListId,

        geneSetCounts,
        overview.getOverlapGeneCount(),
        overview.getUniverseGeneCount());

    // Unfiltered gene-set count
    overview.setOverlapGeneSetCount(rawResults.size());

    // Statistical adjustment
    log.info("Adjusting raw gene set results @ {}...", watch);
    val adjustedResults = adjustRawGeneSetResults(params.getFdr(), rawResults);

    // Keep only the number of results that the user requested
    val limitedAdjustedResults = limitGeneSetResults(adjustedResults, params.getMaxGeneSetCount());

    log.info("Calculating final gene set results @ {}...", watch);
    calculateFinalGeneSetResults(query, universe, inputGeneListId, limitedAdjustedResults);

    // Update state for UI polling
    analysis.setOverview(overview);
    analysis.setResults(limitedAdjustedResults);
    analysis.setState(FINISHED);

    log.info("Updating analysis @ {} ...", watch);
    analysisRepository.update(analysis);

    log.info("Finished analyzing in {}", watch);
  }

  private Overview calculateOverview(Query query, Universe universe, UUID inputGeneListId) {
    return new Overview()
        .setOverlapGeneCount(countGenes(overlapQuery(query, universe, inputGeneListId)))
        .setUniverseGeneCount(countUniverseGenes(universe))
        .setUniverseGeneSetCount(countUniverseGeneSets(universe));
  }

  private List<Count> calculateGeneSetCounts(Query query, Universe universe, UUID inputGeneListId) {
    val overlapQuery = overlapQuery(query, universe, inputGeneListId);
    val response = geneRepository.findGeneSetCounts(overlapQuery);
    val geneSetFacet = getUniverseTermsFacet(response, universe);

    return getFacetCounts(geneSetFacet);
  }

  private List<Result> calculateRawGeneSetsResults(Query query, Universe universe, UUID inputGeneListId,
      List<Count> geneSetGeneCounts, int overlapGeneCount, int universeGeneCount) {
    val rawResults = Lists.<Result> newArrayList();
    for (int i = 0; i < geneSetGeneCounts.size(); i++) {
      val geneSetCount = geneSetGeneCounts.get(i);
      val geneSetId = geneSetCount.getId();

      log.info("[{}/{}] Processing {}", new Object[] { i + 1, geneSetGeneCounts.size(), geneSetId });
      val rawResult = calculateRawGeneSetResult(
          query,
          universe,
          inputGeneListId,
          geneSetId,

          // Formula inputs
          geneSetCount.getValue(),
          overlapGeneCount,
          universeGeneCount
          );

      // Add result for the current gene-set
      rawResults.add(rawResult);
    }

    return rawResults;
  }

  private Result calculateRawGeneSetResult(Query query, Universe universe, UUID inputGeneListId,
      String geneSetId, int geneSetGeneCount, int overlapGeneCount, int universeGeneCount) {
    val geneSetOverlapQuery = geneSetOverlapQuery(query, universe, inputGeneListId, geneSetId);

    // "#Genes in overlap"
    val geneSetOverlapGeneCount = countGenes(geneSetOverlapQuery);

    // Statistics
    val expectedValue = calculateExpectedValue(
        overlapGeneCount,
        geneSetGeneCount, universeGeneCount);
    val pValue = calculateHypergeometricTest(
        geneSetOverlapGeneCount, overlapGeneCount,
        geneSetGeneCount, universeGeneCount);

    // Assemble
    return new Result()
        .setGeneSetId(geneSetId)

        .setGeneCount(geneSetGeneCount)
        .setOverlapGeneCount(overlapGeneCount)

        .setExpectedValue(expectedValue)
        .setPValue(pValue);
  }

  private void calculateFinalGeneSetResults(Query query, Universe universe, UUID inputGeneListId,
      List<Result> limitedAdjustedResults) {
    for (int i = 0; i < limitedAdjustedResults.size(); i++) {
      val geneSetResult = limitedAdjustedResults.get(i);
      val geneSetId = geneSetResult.getGeneSetId();

      log.info("[{}/{}] Post-processing {}", new Object[] { i + 1, limitedAdjustedResults.size(), geneSetId });
      val geneSetOverlapQuery = geneSetOverlapQuery(query, universe, inputGeneListId, geneSetId);

      // Update
      geneSetResult
          .setGeneSetName(findGeneSetName(geneSetId))
          .setOverlapDonorCount(countDonors(geneSetOverlapQuery))
          .setOverlapMutationCount(countMutations(geneSetOverlapQuery));
    }
  }

  private List<String> findInputGeneList(Query query, int maxGeneCount) {
    val limitedGeneQuery = Query.builder()
        .fields(idField())
        .filters(query.getFilters())
        .size(maxGeneCount)
        .sort(query.getSort())
        .order(query.getOrder().toString())
        .build();

    return getHitIds(geneRepository.findAllCentric(limitedGeneQuery));
  }

  private String findGeneSetName(String geneSetId) {
    val nameField = INDEX_GENE_SETS_NAME_FIELD_NAME;

    return geneSetRepository.findOne(geneSetId, nameField).get(nameField).toString();
  }

  private int countGenes(AndQuery query) {
    return (int) geneRepository.countIntersection(query);
  }

  private int countDonors(AndQuery query) {
    return (int) donorRepository.countIntersection(query);
  }

  private int countMutations(AndQuery query) {
    return (int) mutationRepository.countIntersection(query);
  }

  private int countUniverseGenes(Universe universe) {
    if (universe.isGo()) {
      val geneSet = geneSetRepository.findOne(universe.getGeneSetId(), API_GENE_COUNT_FIELD_NAME);

      return ((Long) geneSet.get(INDEX_GENE_COUNT_FIELD_NAME)).intValue();
    } else {
      return (int) geneRepository.count(Query.builder().filters(universe.getFilter()).build());
    }
  }

  private int countUniverseGeneSets(Universe universe) {
    return geneSetRepository.countDecendants(universe.getGeneSetType(), Optional.fromNullable(universe.getGeneSetId()));
  }

  @SneakyThrows
  private void indexInputGeneList(UUID inputGeneListId, List<String> inputGeneList) {
    termLookupService.createTermsLookup(GENE_IDS, inputGeneListId, inputGeneList);
  }

  private static List<Result> limitGeneSetResults(List<Result> results, int maxGeneSetCount) {
    return results.size() < maxGeneSetCount ? results : results.subList(0, maxGeneSetCount);
  }

}
