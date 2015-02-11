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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.dcc.portal.pql.utils.TestingHelpers.createEsAst;
import static org.icgc.dcc.common.core.util.FormatUtils._;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.dcc.portal.pql.es.ast.AndNode;
import org.dcc.portal.pql.es.ast.BoolNode;
import org.dcc.portal.pql.es.ast.ExpressionNode;
import org.dcc.portal.pql.es.ast.FieldsNode;
import org.dcc.portal.pql.es.ast.FilterNode;
import org.dcc.portal.pql.es.ast.GreaterEqualNode;
import org.dcc.portal.pql.es.ast.GreaterThanNode;
import org.dcc.portal.pql.es.ast.LessEqualNode;
import org.dcc.portal.pql.es.ast.LessThanNode;
import org.dcc.portal.pql.es.ast.MustBoolNode;
import org.dcc.portal.pql.es.ast.NestedNode;
import org.dcc.portal.pql.es.ast.NotNode;
import org.dcc.portal.pql.es.ast.RangeNode;
import org.dcc.portal.pql.es.ast.RootNode;
import org.dcc.portal.pql.es.ast.TermNode;
import org.dcc.portal.pql.es.ast.TerminalNode;
import org.dcc.portal.pql.es.ast.TermsNode;
import org.dcc.portal.pql.es.model.RequestType;
import org.junit.Test;

@Slf4j
public class PqlParseListenerTest {

  PqlParseListener listener = new PqlParseListener(new QueryContext());

  @Test
  public void filtersTest() {
    val query =
        "or(gt(a, 10), ge(b, 20.2)), eq(c, 100), ne(d, 200), and(lt(e, 30), le(f, 40)), in(sex, 'male', 'female')";
    val esAst = createEsAst(query);
    log.info("{}", esAst);
    assertFilterStructure(esAst);
    val mustNode = (MustBoolNode) esAst.getFirstChild().getFirstChild().getFirstChild();
    assertThat(mustNode.childrenCount()).isEqualTo(5);

    val orNode = mustNode.getFirstChild();
    assertThat(orNode.childrenCount()).isEqualTo(2);

    // gt(age, 10)
    val gtRangeNode = (RangeNode) orNode.getFirstChild();
    assertThat(gtRangeNode.childrenCount()).isEqualTo(1);
    assertThat(gtRangeNode.getName()).isEqualTo("a");
    val gtRangeNode1Child = (GreaterThanNode) gtRangeNode.getFirstChild();
    assertThat(gtRangeNode1Child.childrenCount()).isEqualTo(1);
    assertThat(gtRangeNode1Child.getValue()).isEqualTo(10);

    // ge(age, 20)
    val geRangeNode = (RangeNode) orNode.getChild(1);
    assertThat(geRangeNode.childrenCount()).isEqualTo(1);
    assertThat(geRangeNode.getName()).isEqualTo("b");
    val gtRangeNode2Child = (GreaterEqualNode) geRangeNode.getFirstChild();
    assertThat(gtRangeNode2Child.childrenCount()).isEqualTo(1);
    assertThat(gtRangeNode2Child.getValue()).isEqualTo(20.2);

    // eq(c, 100)
    val eqNode = (TermNode) mustNode.getChild(1);
    assertThat(eqNode.childrenCount()).isEqualTo(2);
    assertThat(eqNode.getNameNode().getValue()).isEqualTo("c");
    assertThat(eqNode.getValueNode().getValue()).isEqualTo(100);

    // ne(d, 200)
    val notNode = (NotNode) mustNode.getChild(2);
    assertThat(notNode.childrenCount()).isEqualTo(1);
    childrenContainValue(notNode, "d");
    childrenContainValue(notNode, 200);

    val andNode = (AndNode) mustNode.getChild(3);
    assertThat(andNode.childrenCount()).isEqualTo(2);

    // lt(e, 30)
    val ltRangeNode = (RangeNode) andNode.getFirstChild();
    assertThat(ltRangeNode.childrenCount()).isEqualTo(1);
    assertThat(ltRangeNode.getName()).isEqualTo("e");
    val ltRangeNodeChild = (LessThanNode) ltRangeNode.getFirstChild();
    assertThat(ltRangeNodeChild.childrenCount()).isEqualTo(1);
    assertThat(ltRangeNodeChild.getValue()).isEqualTo(30);

    // le(f, 40)
    val leRangeNode = (RangeNode) andNode.getChild(1);
    assertThat(leRangeNode.childrenCount()).isEqualTo(1);
    assertThat(leRangeNode.getName()).isEqualTo("f");
    val leRangeNodeChild = (LessEqualNode) leRangeNode.getFirstChild();
    assertThat(leRangeNodeChild.childrenCount()).isEqualTo(1);
    assertThat(leRangeNodeChild.getValue()).isEqualTo(40);

    // in(sex, 'male', 'female')
    val termsNode = (TermsNode) mustNode.getChild(4);
    assertThat(termsNode.childrenCount()).isEqualTo(2);
    assertThat(termsNode.getField()).isEqualTo("sex");
    val maleNode = (TerminalNode) termsNode.getFirstChild();
    assertThat(maleNode.getValue()).isEqualTo("male");
    val femaleNode = (TerminalNode) termsNode.getChild(1);
    assertThat(femaleNode.getValue()).isEqualTo("female");
  }

  @Test
  public void selectTest() {
    val esAst = createEsAst("select(age, gender)", listener);
    assertThat(esAst.childrenCount()).isEqualTo(1);
    val fieldsNode = (FieldsNode) esAst.getFirstChild();
    assertThat(fieldsNode.childrenCount()).isEqualTo(2);
    assertThat(fieldsNode.getFields()).containsOnly("age", "gender");
  }

  @Test
  public void countTest() {
    val esAst = createEsAst("count()", listener);
    assertThat(listener.getQueryContext().getRequestType()).isEqualTo(RequestType.COUNT);
    assertThat(esAst).isExactlyInstanceOf(RootNode.class);
    assertThat(esAst.childrenCount()).isEqualTo(0);
  }

  @Test
  public void countTest_withFilters() {
    val esAst = createEsAst("count(),eq(age, 10)", listener);
    assertThat(esAst).isExactlyInstanceOf(RootNode.class);
    assertThat(esAst.getFirstChild()).isExactlyInstanceOf(FilterNode.class);
    assertThat(esAst.getFirstChild().getFirstChild()).isExactlyInstanceOf(BoolNode.class);
    assertThat(esAst.getFirstChild().getFirstChild().getFirstChild()).isExactlyInstanceOf(MustBoolNode.class);
    val mustNode = esAst.getFirstChild().getFirstChild().getFirstChild();
    assertThat(mustNode.getFirstChild().childrenCount()).isEqualTo(2);
    childrenContainValue(mustNode, "age");
    childrenContainValue(mustNode, 10);
  }

  @Test
  public void nestedTest() {
    val esAst = createEsAst("nested(gene, eq(gene._donor_id, 'D01'))", listener);
    assertThat(esAst).isExactlyInstanceOf(RootNode.class);
    assertThat(esAst.childrenCount()).isEqualTo(1);
    assertThat(esAst.getFirstChild()).isExactlyInstanceOf(FilterNode.class);
    assertThat(esAst.getFirstChild().childrenCount()).isEqualTo(1);
    assertThat(esAst.getFirstChild().getFirstChild()).isExactlyInstanceOf(BoolNode.class);
    assertThat(esAst.getFirstChild().getFirstChild().childrenCount()).isEqualTo(1);
    assertThat(esAst.getFirstChild().getFirstChild().getFirstChild()).isExactlyInstanceOf(MustBoolNode.class);
    assertThat(esAst.getFirstChild().getFirstChild().getFirstChild().childrenCount()).isEqualTo(1);
    assertThat(esAst.getFirstChild().getFirstChild().getFirstChild().getFirstChild()).isExactlyInstanceOf(
        NestedNode.class);
  }

  private static void assertFilterStructure(ExpressionNode esAst) {
    assertThat(esAst).isExactlyInstanceOf(RootNode.class);
    assertThat(esAst.childrenCount()).isEqualTo(1);
    val postFilterNode = (FilterNode) esAst.getFirstChild();
    assertThat(postFilterNode.childrenCount()).isEqualTo(1);
    val boolNode = (BoolNode) postFilterNode.getFirstChild();
    assertThat(boolNode.childrenCount()).isEqualTo(1);
    assertThat(boolNode.getFirstChild()).isExactlyInstanceOf(MustBoolNode.class);
  }

  /**
   * Checks if one of the children contains {@code value}. Fails if the value was found in more that one child.
   */
  private static void childrenContainValue(ExpressionNode parent, Object value) {
    boolean found = false;
    for (val child : parent.getChildren()) {

      if (child instanceof TermNode) {
        if (termNodeContainsValue((TermNode) child, value, found)) {
          found = true;
        }
      }
      else if (child instanceof NotNode) {
        if (termNodeContainsValue((TermNode) child.getFirstChild(), value, found)) {
          found = true;
        }
      }

    }

    if (!found) {
      fail(_("Value %s was not found in %s", value, parent));
    }

  }

  private static boolean termNodeContainsValue(TermNode node, Object value, boolean foundBefore) {
    val nodeName = node.getNameNode().getValue();
    val nodeValue = node.getValueNode().getValue();
    if (nodeName.equals(value) || nodeValue.equals(value)) {
      if (foundBefore) {
        fail(_("Parent contains more than one value of %s", value.toString()));
      } else {
        return true;
      }
    }

    return false;
  }

}
