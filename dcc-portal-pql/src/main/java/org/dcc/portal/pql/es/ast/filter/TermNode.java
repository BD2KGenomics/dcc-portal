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
package org.dcc.portal.pql.es.ast.filter;

import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.val;

import org.dcc.portal.pql.es.ast.ExpressionNode;
import org.dcc.portal.pql.es.ast.TerminalNode;
import org.dcc.portal.pql.es.visitor.NodeVisitor;

@Value
@EqualsAndHashCode(callSuper = true)
public class TermNode extends ExpressionNode {

  @NonNull
  TerminalNode nameNode;

  @NonNull
  TerminalNode valueNode;

  public TermNode(@NonNull TerminalNode name, @NonNull TerminalNode value) {
    super(new ExpressionNode[] { name, value });
    this.nameNode = name;
    this.valueNode = value;
  }

  public TermNode(@NonNull String name, @NonNull Object value) {
    checkState(!name.isEmpty(), "Term node has an empty name.");
    val nameNode = new TerminalNode(name);
    val valueNode = new TerminalNode(value);
    super.addChildren(nameNode, valueNode);
    this.nameNode = nameNode;
    this.valueNode = valueNode;
  }

  @Override
  public <T, A> T accept(@NonNull NodeVisitor<T, A> visitor, @NonNull Optional<A> context) {
    return visitor.visitTerm(this, context);
  }

}
