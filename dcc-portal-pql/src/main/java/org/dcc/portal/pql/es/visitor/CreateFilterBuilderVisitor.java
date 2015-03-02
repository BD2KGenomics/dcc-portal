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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.dcc.portal.pql.es.model.RequestType.COUNT;
import static org.dcc.portal.pql.es.utils.Nodes.filterChildren;
import static org.elasticsearch.index.query.FilterBuilders.andFilter;
import static org.elasticsearch.index.query.FilterBuilders.boolFilter;
import static org.elasticsearch.index.query.FilterBuilders.nestedFilter;
import static org.elasticsearch.index.query.FilterBuilders.notFilter;
import static org.elasticsearch.index.query.FilterBuilders.orFilter;
import static org.elasticsearch.index.query.FilterBuilders.rangeFilter;
import static org.elasticsearch.index.query.FilterBuilders.termFilter;
import static org.elasticsearch.index.query.FilterBuilders.termsFilter;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import java.util.Stack;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.dcc.portal.pql.es.ast.ExpressionNode;
import org.dcc.portal.pql.es.ast.FieldsNode;
import org.dcc.portal.pql.es.ast.LimitNode;
import org.dcc.portal.pql.es.ast.NestedNode;
import org.dcc.portal.pql.es.ast.QueryNode;
import org.dcc.portal.pql.es.ast.SortNode;
import org.dcc.portal.pql.es.ast.TermNode;
import org.dcc.portal.pql.es.ast.TerminalNode;
import org.dcc.portal.pql.es.ast.TermsNode;
import org.dcc.portal.pql.es.ast.aggs.AggregationsNode;
import org.dcc.portal.pql.es.ast.filter.AndNode;
import org.dcc.portal.pql.es.ast.filter.BoolNode;
import org.dcc.portal.pql.es.ast.filter.FilterNode;
import org.dcc.portal.pql.es.ast.filter.GreaterEqualNode;
import org.dcc.portal.pql.es.ast.filter.GreaterThanNode;
import org.dcc.portal.pql.es.ast.filter.LessEqualNode;
import org.dcc.portal.pql.es.ast.filter.LessThanNode;
import org.dcc.portal.pql.es.ast.filter.MustBoolNode;
import org.dcc.portal.pql.es.ast.filter.NotNode;
import org.dcc.portal.pql.es.ast.filter.OrNode;
import org.dcc.portal.pql.es.ast.filter.RangeNode;
import org.dcc.portal.pql.meta.AbstractTypeModel;
import org.dcc.portal.pql.qe.QueryContext;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.search.sort.SortOrder;

import com.google.common.collect.Lists;

// TODO: create static factory construction methods that return cached visitor for each type model.
// FIXME: Remove code which is present in the FilterBuilderVisitor
@Slf4j
@RequiredArgsConstructor
public class CreateFilterBuilderVisitor extends NodeVisitor<FilterBuilder> {

  @NonNull
  private final Client client;
  @NonNull
  private final AbstractTypeModel typeModel;
  private final Stack<FilterBuilder> stack = new Stack<FilterBuilder>();

  @Override
  public FilterBuilder visitBool(@NonNull BoolNode node) {
    BoolFilterBuilder resultBuilder = boolFilter();
    val mustNode = getChild(node, MustBoolNode.class);
    if (mustNode != null) {
      resultBuilder = resultBuilder.must(visitChildren(mustNode));
    }

    return resultBuilder;
  }

  private FilterBuilder[] visitChildren(ExpressionNode node) {
    log.debug("Visiting Bool child: {}", node);
    val result = Lists.<FilterBuilder> newArrayList();
    for (val child : node.getChildren()) {
      log.debug("Sub-child: {}", child);
      result.add(child.accept(this));
    }

    return result.toArray(new FilterBuilder[result.size()]);
  }

  @Override
  public FilterBuilder visitTerm(@NonNull TermNode node) {
    val name = node.getNameNode().getValue().toString();
    val value = node.getValueNode().getValue();
    log.debug("[visitTerm] Name: {}, Value: {}", name, value);
    val result = termFilter(name, value);

    return createNestedFilter(node, name, result);
  }

  /**
   * Wraps {@code field} in a {@code nested} query if the {@code node} which contains the {@code field} does not have a
   * {@link NestedNode} parent.
   */
  private FilterBuilder createNestedFilter(ExpressionNode node, String field, FilterBuilder sourceFilter) {
    if (typeModel.isNested(field)) {
      if (!node.hasNestedParent()) {
        val nestedPath = typeModel.getNestedPath(field);
        log.debug("[visitTerm] Node '{}' does not have a nested parent. Nesting at path '{}'",
            node, nestedPath);
        val nestedFilter = nestedFilter(nestedPath, sourceFilter);

        return nestedFilter;
      }
    }

    return sourceFilter;
  }

  private static <T> T getChild(BoolNode boolNode, Class<T> type) {
    val children = filterChildren(boolNode, type);
    checkState(children.size() < 2, "A BoolExpressionNode can contain only a single node of type %s",
        type.getSimpleName());

    if (children.isEmpty()) {
      return null;
    } else {
      return children.get(0);
    }
  }

  @Override
  public FilterBuilder visitFilter(@NonNull FilterNode node) {
    return visitBool((BoolNode) node.getFirstChild());
  }

  public SearchRequestBuilder visit(@NonNull ExpressionNode node, @NonNull QueryContext queryContext) {
    SearchRequestBuilder result = client
        .prepareSearch(queryContext.getIndex())
        .setTypes(queryContext.getType().getId());

    if (queryContext.getRequestType() == COUNT) {
      log.debug("Setting search type to count");
      result = result.setSearchType(SearchType.COUNT);
    }

    for (val child : node.getChildren()) {
      if (child instanceof FilterNode) {
        result.setPostFilter(child.accept(this));
      } else if (child instanceof QueryNode) {
        val filtersNode = child.getOptionalFirstChild();
        FilterBuilder queryFilters = null;
        if (filtersNode.isPresent()) {
          queryFilters = filtersNode.get().accept(this);
        }

        result.setQuery(filteredQuery(matchAllQuery(), queryFilters));
      } else if (child instanceof AggregationsNode) {
        addAggregations(child, result);
      } else if (child instanceof FieldsNode) {
        val fieldsNode = (FieldsNode) child;
        String[] children = fieldsNode.getFields().toArray(new String[fieldsNode.getFields().size()]);
        result.addFields(children);
      } else if (child instanceof LimitNode) {
        val limitNode = (LimitNode) child;
        result.setFrom(limitNode.getFrom());
        result.setSize(limitNode.getSize());
      } else if (child instanceof SortNode) {
        val sortNode = (SortNode) child;
        for (val entry : sortNode.getFields().entrySet()) {
          result.addSort(entry.getKey(), SortOrder.valueOf(entry.getValue().toString()));
        }
      }
    }

    return result;
  }

  @Override
  public FilterBuilder visitNot(@NonNull NotNode node) {
    val childrenCount = node.childrenCount();
    checkState(childrenCount == 1, "NotNode can have only one child. Found {}", childrenCount);

    return notFilter(node.getFirstChild().accept(this));
  }

  @Override
  public FilterBuilder visitRange(@NonNull RangeNode node) {
    checkState(node.childrenCount() > 0, "RangeNode has no children");

    stack.push(rangeFilter(node.getFieldName()));
    for (val child : node.getChildren()) {
      child.accept(this);
    }

    return createNestedFilter(node, node.getFieldName(), stack.pop());
  }

  @Override
  public FilterBuilder visitGreaterEqual(@NonNull GreaterEqualNode node) {
    val rangeFilter = (RangeFilterBuilder) stack.peek();
    checkNotNull(rangeFilter, "Could not find the RangeFilter on the stack");
    rangeFilter.gte(node.getValue());

    return rangeFilter;
  }

  @Override
  public FilterBuilder visitGreaterThan(@NonNull GreaterThanNode node) {
    val rangeFilter = (RangeFilterBuilder) stack.peek();
    checkNotNull(rangeFilter, "Could not find the RangeFilter on the stack");
    rangeFilter.gt(node.getValue());

    return rangeFilter;
  }

  @Override
  public FilterBuilder visitLessEqual(@NonNull LessEqualNode node) {
    val rangeFilter = (RangeFilterBuilder) stack.peek();
    checkNotNull(rangeFilter, "Could not find the RangeFilter on the stack");
    rangeFilter.lte(node.getValue());

    return rangeFilter;
  }

  @Override
  public FilterBuilder visitLessThan(@NonNull LessThanNode node) {
    val rangeFilter = (RangeFilterBuilder) stack.peek();
    checkNotNull(rangeFilter, "Could not find the RangeFilter on the stack");
    rangeFilter.lt(node.getValue());

    return rangeFilter;
  }

  @Override
  public FilterBuilder visitAnd(@NonNull AndNode node) {
    log.debug("Visiting And: {}", node);
    val childrenFilters = Lists.<FilterBuilder> newArrayList();
    for (val child : node.getChildren()) {
      childrenFilters.add(child.accept(this));
    }

    return andFilter(childrenFilters.toArray(new FilterBuilder[childrenFilters.size()]));
  }

  @Override
  public FilterBuilder visitOr(@NonNull OrNode node) {
    log.debug("Visiting Or: {}", node);
    val childrenFilters = Lists.<FilterBuilder> newArrayList();
    for (val child : node.getChildren()) {
      childrenFilters.add(child.accept(this));
    }

    return orFilter(childrenFilters.toArray(new FilterBuilder[childrenFilters.size()]));
  }

  @Override
  public FilterBuilder visitTerms(@NonNull TermsNode node) {
    val values = Lists.newArrayList();
    for (val child : node.getChildren()) {
      values.add(((TerminalNode) child).getValue());
    }

    return createNestedFilter(node, node.getField(), termsFilter(node.getField(), values));
  }

  @Override
  public FilterBuilder visitNested(NestedNode node) {
    log.debug("Visiting Nested: {}", node);

    return nestedFilter(node.getPath(), node.getFirstChild().accept(this));
  }

  private void addAggregations(ExpressionNode aggregationsNode, SearchRequestBuilder result) {
    log.debug("Adding aggregations for AggregationsNode\n{}", aggregationsNode);
    for (val child : aggregationsNode.getChildren()) {
      val aggregationBuilder = child.accept(Visitors.createAggregationBuilderVisitor(typeModel));
      result.addAggregation(aggregationBuilder);
    }
  }

}
