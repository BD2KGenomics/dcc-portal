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
package org.dcc.portal.pql.es.internal.builder;

import java.util.List;

import lombok.NonNull;
import lombok.val;

import org.assertj.core.util.Lists;
import org.dcc.portal.pql.es.builder.BoolBuilder;
import org.dcc.portal.pql.es.builder.Builders;
import org.dcc.portal.pql.es.node.BoolExpressionNode;
import org.dcc.portal.pql.es.node.ExpressionNode;
import org.dcc.portal.pql.es.node.MustExpressionNode;
import org.dcc.portal.pql.es.utils.Helpers;

public class BoolBuilderImpl implements BoolBuilder {

  private BoolExpressionNode result;
  private List<ExpressionNode> children = Lists.newArrayList();

  @Override
  public BoolBuilder mustTerm(@NonNull MustExpressionNode mustNode) {
    children.add(mustNode);

    return this;
  }

  @Override
  public BoolBuilder mustTerm(@NonNull String name, @NonNull Object value) {
    MustExpressionNode mustNode = Helpers.getChildByType(children, MustExpressionNode.class);
    val termNode = Builders.termNode(name, value);
    if (mustNode == null) {
      mustNode = Builders.mustNode(null, termNode);
      children.add(mustNode);
    } else {
      mustNode.addChild(termNode);
    }

    return this;
  }

  @Override
  public BoolBuilder shouldTerm() {
    return this;

  }

  @Override
  public BoolBuilder shouldNotTerm() {
    return this;

  }

  @Override
  public BoolExpressionNode build() {
    result = new BoolExpressionNode(null, children);

    for (val child : children) {
      child.setParent(null);
    }

    return result;
  }

}
