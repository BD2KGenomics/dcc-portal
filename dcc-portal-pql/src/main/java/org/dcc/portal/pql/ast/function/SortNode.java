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
package org.dcc.portal.pql.ast.function;

import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import org.dcc.portal.pql.ast.PqlNode;
import org.dcc.portal.pql.ast.Type;
import org.dcc.portal.pql.ast.visitor.PqlNodeVisitor;
import org.dcc.portal.pql.es.model.Order;

import com.google.common.collect.Maps;

@Value
@EqualsAndHashCode(callSuper = false)
public class SortNode extends PqlNode {

  Map<String, Order> fields = Maps.newHashMap();

  public void addField(@NonNull String field, @NonNull Order order) {
    fields.put(field, order);
  }

  public void addAscSort(@NonNull String field) {
    fields.put(field, Order.ASC);
  }

  public void addDescSort(@NonNull String field) {
    fields.put(field, Order.DESC);
  }

  @Override
  public Type type() {
    return Type.SORT;
  }

  @Override
  public <T, A> T accept(@NonNull PqlNodeVisitor<T, A> visitor, @NonNull Optional<A> context) {
    return visitor.visitSort(this, context);
  }

  @Override
  public SortNode toSortNode() {
    return this;
  }

}
