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
package org.dcc.portal.pql.es.visitor.aggs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import lombok.val;

import org.dcc.portal.pql.es.ast.NestedNode;
import org.dcc.portal.pql.es.ast.filter.BoolNode;
import org.dcc.portal.pql.es.ast.filter.FilterNode;
import org.dcc.portal.pql.es.ast.filter.MustBoolNode;
import org.dcc.portal.pql.es.ast.filter.TermNode;
import org.dcc.portal.pql.meta.IndexModel;
import org.junit.Test;

public class SearchNonNestedFieldsVisitorTest {

  SearchNonNestedFieldsVisitor visitor = new SearchNonNestedFieldsVisitor();

  @Test
  public void nonNestedTest() {
    val filter = new FilterNode(new BoolNode(new MustBoolNode(new TermNode("platformNested", "pl"))));
    val result = filter.accept(visitor, createContext("transcript"));
    assertThat(result).isTrue();
  }

  @Test
  public void nestedTest() {
    val filter = new FilterNode(new BoolNode(new MustBoolNode(new TermNode("transcriptId", "pl"))));
    val result = filter.accept(visitor, createContext("transcript"));
    assertThat(result).isFalse();
  }

  @Test
  public void nonNestedWithNestedTest() {
    val filter = new FilterNode(new BoolNode(new MustBoolNode(
        new TermNode("platformNested", ""),
        new TermNode("transcriptId", ""))));
    val result = filter.accept(visitor, createContext("transcript"));
    assertThat(result).isTrue();
  }

  @Test
  public void nestedWithNestedNodeTest() {
    val filter = new FilterNode(new NestedNode("ssm_occurrence.observation", new TermNode("platformNested", "")));
    val result = filter.accept(visitor, createContext("ssm_occurrence"));
    assertThat(result).isFalse();
  }

  @Test
  public void differentNestedLevelTest() {
    val filter = new FilterNode(new NestedNode("transcript", new TermNode("transcriptId", "")));
    val result = filter.accept(visitor, createContext("ssm_occurrence"));
    assertThat(result).isTrue();
  }

  private Optional<VisitContext> createContext(String path) {
    return Optional.of(new VisitContext(path, IndexModel.getMutationCentricTypeModel()));
  }

}
