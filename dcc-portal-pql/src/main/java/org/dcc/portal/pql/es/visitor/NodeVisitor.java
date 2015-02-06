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

import org.dcc.portal.pql.es.ast.AndNode;
import org.dcc.portal.pql.es.ast.BoolNode;
import org.dcc.portal.pql.es.ast.FacetsNode;
import org.dcc.portal.pql.es.ast.FieldsNode;
import org.dcc.portal.pql.es.ast.FilterNode;
import org.dcc.portal.pql.es.ast.GreaterEqualNode;
import org.dcc.portal.pql.es.ast.GreaterThanNode;
import org.dcc.portal.pql.es.ast.LessEqualNode;
import org.dcc.portal.pql.es.ast.LessThanNode;
import org.dcc.portal.pql.es.ast.LimitNode;
import org.dcc.portal.pql.es.ast.MustBoolNode;
import org.dcc.portal.pql.es.ast.NestedNode;
import org.dcc.portal.pql.es.ast.NotNode;
import org.dcc.portal.pql.es.ast.OrNode;
import org.dcc.portal.pql.es.ast.QueryNode;
import org.dcc.portal.pql.es.ast.RangeNode;
import org.dcc.portal.pql.es.ast.RootNode;
import org.dcc.portal.pql.es.ast.SortNode;
import org.dcc.portal.pql.es.ast.TermNode;
import org.dcc.portal.pql.es.ast.TerminalNode;
import org.dcc.portal.pql.es.ast.TermsFacetNode;
import org.dcc.portal.pql.es.ast.TermsNode;

public abstract class NodeVisitor<T> {

  private static final String DEFAULT_ERROR_MESSAGE = "The method is not implemented by the subclass";

  public T visitFilter(FilterNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitNested(NestedNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitBool(BoolNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitMustBool(MustBoolNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitTerm(TermNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitTerms(TermsNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitNot(NotNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitRoot(RootNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitSort(SortNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitTerminal(TerminalNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitRange(RangeNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitGreaterEqual(GreaterEqualNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitGreaterThan(GreaterThanNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitLessEqual(LessEqualNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitLessThan(LessThanNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitLimit(LimitNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitAnd(AndNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitOr(OrNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitFacets(FacetsNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitFields(FieldsNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitQuery(QueryNode node) {
    return defaultUnimplementedMethod();
  }

  public T visitTermsFacet(TermsFacetNode node) {
    return defaultUnimplementedMethod();
  }

  private T defaultUnimplementedMethod() {
    throw new UnsupportedOperationException(DEFAULT_ERROR_MESSAGE);
  }

}
