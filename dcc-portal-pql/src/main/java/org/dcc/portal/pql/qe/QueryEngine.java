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
package org.dcc.portal.pql.qe;

import static java.lang.String.format;
import static org.dcc.portal.pql.meta.Type.DONOR_CENTRIC;
import static org.dcc.portal.pql.meta.Type.GENE_CENTRIC;
import static org.dcc.portal.pql.meta.Type.MUTATION_CENTRIC;
import static org.dcc.portal.pql.meta.Type.OBSERVATION_CENTRIC;
import static org.dcc.portal.pql.meta.Type.PROJECT;
import lombok.NonNull;
import lombok.val;

import org.dcc.portal.pql.es.ast.ExpressionNode;
import org.dcc.portal.pql.es.utils.EsAstTransformator;
import org.dcc.portal.pql.es.utils.ParseTrees;
import org.dcc.portal.pql.meta.Type;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;

public class QueryEngine {

  private static final EsAstTransformator esAstTransformator = new EsAstTransformator();
  private final EsRequestBuilder requestBuilder;

  private final QueryContext donorContext;
  private final QueryContext geneContext;
  private final QueryContext mutationContext;
  private final QueryContext observationContext;
  private final QueryContext projectContext;

  public QueryEngine(@NonNull Client client, @NonNull String index) {
    this.requestBuilder = new EsRequestBuilder(client);

    this.donorContext = new QueryContext(index, DONOR_CENTRIC);
    this.geneContext = new QueryContext(index, GENE_CENTRIC);
    this.mutationContext = new QueryContext(index, MUTATION_CENTRIC);
    this.observationContext = new QueryContext(index, OBSERVATION_CENTRIC);
    this.projectContext = new QueryContext(index, PROJECT);
  }

  public SearchRequestBuilder execute(@NonNull String pql, @NonNull Type type) {
    val context = createQueryContext(type);
    ExpressionNode esAst = resolvePql(pql, context);
    esAst = esAstTransformator.process(esAst, context);

    return requestBuilder.buildSearchRequest(esAst, context);
  }

  private static ExpressionNode resolvePql(String query, QueryContext context) {
    val parser = ParseTrees.getParser(query);
    val pqlListener = new PqlParseListener(context);
    parser.addParseListener(pqlListener);
    parser.statement();

    return pqlListener.getEsAst();
  }

  private QueryContext createQueryContext(Type type) {
    switch (type) {
    case DONOR_CENTRIC:
      return donorContext;
    case GENE_CENTRIC:
      return geneContext;
    case MUTATION_CENTRIC:
      return mutationContext;
    case OBSERVATION_CENTRIC:
      return observationContext;
    case PROJECT:
      return projectContext;
    default:
      throw new IllegalArgumentException(format("Type %s is not supported", type.getId()));
    }
  }

}
