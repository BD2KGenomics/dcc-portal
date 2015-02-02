/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.dcc.portal.pql.es.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.icgc.dcc.portal.model.IndexModel.Type.DONOR_CENTRIC;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.dcc.portal.pql.es.ast.ExpressionNode;
import org.dcc.portal.pql.es.model.RequestType;
import org.dcc.portal.pql.es.utils.ParseTrees;
import org.dcc.portal.pql.meta.IndexModel;
import org.dcc.portal.pql.qe.PqlParseListener;
import org.dcc.portal.pql.qe.QueryContext;
import org.dcc.portal.pql.utils.BaseElasticsearchTest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

@Slf4j
public class CreateFilterBuilderVisitorTest extends BaseElasticsearchTest {

  private PqlParseListener listener;
  private CreateFilterBuilderVisitor visitor;
  private QueryContext queryContext;

  @Before
  public void setUp() {
    es.execute(createIndexMappings(DONOR_CENTRIC).withData(bulkFile(getClass())));
    visitor = new CreateFilterBuilderVisitor(es.client(), new IndexModel());
    queryContext = new QueryContext();
    queryContext.setType(DONOR_CENTRIC);
    queryContext.setIndex(INDEX_NAME);
    listener = new PqlParseListener(new QueryContext());
  }

  @Test
  public void sortTest() {
    val result = executeQuery("select(donor_age_at_enrollment),sort(-donor_age_at_enrollment)");
    assertThat(result.getHits().getAt(0).getSortValues()[0]).isEqualTo(173L);

  }

  @Test
  public void selectTest() {
    val result = executeQuery("select(donor_age_at_enrollment)");
    val hit = result.getHits().getAt(0);
    assertThat(hit.fields().size()).isEqualTo(1);
    assertThat(hit.field("donor_age_at_enrollment").getValue()).isEqualTo(77);
  }

  @Test
  public void countTest() {
    val esAst = createTree("count()");
    queryContext.setRequestType(RequestType.COUNT);
    val request = visitor.visit(esAst, queryContext);
    val result = request.execute().actionGet();
    assertThat(result.getHits().getTotalHits()).isEqualTo(9);
  }

  @Test
  public void countTest_withFilter() {
    val esAst = createTree("count(), gt(donor_age_at_enrollment, 100)");
    queryContext.setRequestType(RequestType.COUNT);
    val request = visitor.visit(esAst, queryContext);
    val result = request.execute().actionGet();
    assertThat(result.getHits().getTotalHits()).isEqualTo(4);
  }

  @Test
  public void inTest() {
    val result = executeQuery("in(_donor_id, 'DO1', 'DO2')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(2);
    containsOnlyIds(result, "DO1", "DO2");
  }

  @Test
  public void eqTest() {
    val result = executeQuery("eq(_donor_id, 'DO1')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(1);
    assertThat(result.getHits().getAt(0).getId()).isEqualTo("DO1");
  }

  @Test
  public void eqTest_nested() {
    val result = executeQuery("eq(gene.ssm.observation._sample_id, 'SA35')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(1);
    assertThat(result.getHits().getAt(0).getId()).isEqualTo("DO7");
  }

  @Test
  public void neTest() {
    val result = executeQuery("ne(_donor_id, 'DO1')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(8);
    for (val hit : result.getHits()) {
      assertThat(hit.getId()).isNotEqualTo("DO1");
    }
  }

  @Test
  public void neTest_nested() {
    val result = executeQuery("ne(gene._summary._ssm_count, 1)");
    assertThat(result.getHits().getTotalHits()).isEqualTo(3);
    containsOnlyIds(result, "DO3", "DO6", "DO7");
  }

  @Test
  public void gtTest() {
    val result = executeQuery("gt(_donor_id, 'DO5')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(4);
    containsOnlyIds(result, "DO6", "DO7", "DO8", "DO9");
  }

  @Test
  public void geTest() {
    val result = executeQuery("ge(_donor_id, 'DO5')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(5);
    containsOnlyIds(result, "DO5", "DO6", "DO7", "DO8", "DO9");
  }

  @Test
  public void leTest() {
    val result = executeQuery("le(_donor_id, 'DO5')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(5);
    containsOnlyIds(result, "DO1", "DO2", "DO3", "DO4", "DO5");
  }

  @Test
  public void ltTest() {
    val result = executeQuery("lt(_donor_id, 'DO5')");
    assertThat(result.getHits().getTotalHits()).isEqualTo(4);
    containsOnlyIds(result, "DO1", "DO2", "DO3", "DO4");
  }

  @Test
  public void andTest() {
    val result = executeQuery("and(eq(project._project_id, 'OV-AU'), gt(donor_age_at_diagnosis, 100)))");
    assertThat(result.getHits().getTotalHits()).isEqualTo(1);
    assertThat(result.getHits().getAt(0).getId()).isEqualTo("DO2");
  }

  @Test
  public void andTest_rootLevel() {
    val result = executeQuery("eq(project._project_id, 'OV-AU'), gt(donor_age_at_diagnosis, 100))");
    assertThat(result.getHits().getTotalHits()).isEqualTo(1);
    assertThat(result.getHits().getAt(0).getId()).isEqualTo("DO2");
  }

  @Test
  public void orTest() {
    val result = executeQuery("or(eq(project._project_id, 'PACA-AU'), lt(donor_age_at_diagnosis, 100))");
    assertThat(result.getHits().getTotalHits()).isEqualTo(5);
    containsOnlyIds(result, "DO1", "DO4", "DO5", "DO7", "DO9");
  }

  private SearchResponse executeQuery(String query) {
    val esAst = createTree(query);
    val request = visitor.visit(esAst, queryContext);
    log.debug("Request - {}", request);
    val result = request.execute().actionGet();
    log.debug("Result - {}", result);

    return result;
  }

  private ExpressionNode createTree(String query) {
    val parser = ParseTrees.getParser(query);
    parser.addParseListener(listener);
    parser.statement();

    return listener.getEsAst();
  }

  private static void containsOnlyIds(SearchResponse response, String... ids) {
    val resopnseIds = Lists.<String> newArrayList();

    for (val hit : response.getHits()) {
      resopnseIds.add(hit.getId());
    }
    assertThat(resopnseIds).containsOnly(ids);
  }

  @Test
  public void nestedTest() {
    val result = executeQuery("or(" +
        "nested(gene.ssm.observation, " +
        "and(eq(gene.ssm.observation._sample_id, 'SA35'), eq(gene.ssm.observation._specimen_id, 'SP12')))," +
        "nested(gene.ssm.consequence, eq(gene.ssm.consequence.consequence_type, 'missense')))");
    assertThat(result.getHits().getTotalHits()).isEqualTo(3);
    containsOnlyIds(result, "DO1", "DO2", "DO7");
  }

}
