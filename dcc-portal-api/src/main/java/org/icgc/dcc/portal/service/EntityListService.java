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
package org.icgc.dcc.portal.service;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkState;
import static org.icgc.dcc.portal.resource.ResourceUtils.DEFAULT_GENE_MUTATION_SORT;
import static org.supercsv.prefs.CsvPreference.TAB_PREFERENCE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.portal.analysis.UnionAnalyzer;
import org.icgc.dcc.portal.config.PortalProperties;
import org.icgc.dcc.portal.model.BaseEntityList;
import org.icgc.dcc.portal.model.DerivedEntityListDefinition;
import org.icgc.dcc.portal.model.EntityList;
import org.icgc.dcc.portal.model.EntityList.State;
import org.icgc.dcc.portal.model.EntityList.SubType;
import org.icgc.dcc.portal.model.EntityListDefinition;
import org.icgc.dcc.portal.repository.EntityListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.supercsv.io.CsvListWriter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * TODO
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @_(@Autowired))
public class EntityListService {

  @NonNull
  private final EntityListRepository repository;

  @NonNull
  private final UnionAnalyzer analyzer;

  @NonNull
  private final PortalProperties properties;

  private DemoEntityList demoEntityList;

  @PostConstruct
  private void init() {
    val config = properties.getSetOperation();
    val uuid = config.getDemoListUuid();
    val filter = config.getDemoListFilterParam();

    demoEntityList = new DemoEntityList(uuid, filter);
  }

  @SneakyThrows
  private static String toFilterParamForGeneSymbols(@NonNull final String symbolList) {
    // Build the ObjectNode to represent this filterParam: { "gene": "symbol": {"is": ["s1", "s2", ...]}
    val nodeFactory = new JsonNodeFactory(false);
    val root = nodeFactory.objectNode();
    val gene = nodeFactory.objectNode();
    root.put("gene", gene);
    val symbol = nodeFactory.objectNode();
    gene.put("symbol", symbol);
    val isNode = nodeFactory.arrayNode();
    symbol.put("is", isNode);

    final String[] symbols = symbolList.split(",");
    for (val s : symbols) {
      isNode.add(s);
    }
    val result = root.toString();

    return URLEncoder.encode(result, UTF_8.name());
  }

  private final class DemoEntityList {

    private static final String NAME = "DEMO gene set";
    private static final String DESCRIPTION = "Select this DEMO gene set then click on Enrichment Analysis";
    private static final String SORT_BY = DEFAULT_GENE_MUTATION_SORT;

    private final EntityListDefinition.SortOrder SORT_ORDER = EntityListDefinition.SortOrder.ASCENDING;
    private final BaseEntityList.Type TYPE = BaseEntityList.Type.GENE;

    private final EntityListDefinition definition;
    private final EntityList demoList;

    private DemoEntityList(@NonNull final String uuid, @NonNull final String geneSymbols) {
      this.demoList = new EntityList(UUID.fromString(uuid), State.PENDING, 0L, NAME, DESCRIPTION, TYPE);
      this.definition =
          new EntityListDefinition(toFilterParamForGeneSymbols(geneSymbols), SORT_BY, SORT_ORDER, NAME, DESCRIPTION,
              TYPE, 0);
    }
  }

  public EntityList getEntityList(@NonNull final UUID listId) {
    val list = repository.find(listId);

    if (null == list) {
      log.error("No list is found for id: '{}'.", listId);
    } else {
      log.debug("Got enity list: '{}'.", list);
    }
    return list;
  }

  public EntityList createEntityList(
      @NonNull final EntityListDefinition listDefinition) {

    val newList = createAndSaveNewListFrom(listDefinition);

    analyzer.materializeList(newList.getId(), listDefinition);

    return newList;
  }

  @Async
  public void createDemoEntityList() {

    val newList = demoEntityList.demoList;
    val listId = newList.getId();

    val list = repository.find(listId);
    if (null == list) {
      // create if the demo record doesn't exist in the relational database.
      log.info(
          "The demo record in the relational store does not exist therefore is now being recreated: '{}'",
          newList);
      val insertCount = repository.save(newList);
    }

    analyzer.materializeList(listId, demoEntityList.definition);
  }

  public EntityList deriveEntityList(
      @NonNull final DerivedEntityListDefinition listDefinition) {

    val newList =
        (listDefinition.isTransient()) ? createAndSaveNewListFrom(listDefinition, SubType.TRANSIENT) : createAndSaveNewListFrom(listDefinition);

    analyzer.combineLists(newList.getId(), listDefinition);

    return newList;
  }

  private EntityList createAndSaveNewListFrom(final BaseEntityList listDefinition, final SubType subtype) {
    val newList = EntityList.createFromDefinition(listDefinition);
    if (null != subtype) {
      newList.setSubtype(subtype);
    }

    val insertCount = repository.save(newList);
    checkState(insertCount == 1, "Could not save list - Insert count: %s", insertCount);

    return newList;
  }

  private EntityList createAndSaveNewListFrom(final BaseEntityList listDefinition) {
    return createAndSaveNewListFrom(listDefinition, null);
  }

  // Helpers to facilitate exportListItems() only. They should be used in anywhere else.
  private List<List<String>> convertToListOfList(@NonNull final List<String> list) {
    val result = new ArrayList<List<String>>(list.size());
    for (val v : list) {
      result.add(Arrays.asList(v));
    }
    return result;
  }

  private List<List<String>> convertToListOfListForGene(@NonNull final Map<String, String> map) {
    val result = new ArrayList<List<String>>(map.size());
    val entrySet = map.entrySet();
    for (val v : entrySet) {
      result.add(Arrays.asList(v.getKey(), v.getValue()));
    }
    return result;
  }

  public void exportListItems(@NonNull EntityList entityList, @NonNull OutputStream outputStream) throws IOException {
    val content =
        // I need this 'convolution' to achieve the correct type inference to satisfy CsvListWriter.write (List<?>)
        // overload.
        (BaseEntityList.Type.GENE == entityList.getType()) ? convertToListOfListForGene(analyzer
            .retrieveGeneIdsAndSymbolsByListId(entityList.getId())) : convertToListOfList(analyzer
            .retriveListItems(entityList));

    @Cleanup
    val writer = new CsvListWriter(new OutputStreamWriter(outputStream), TAB_PREFERENCE);

    for (val v : content) {
      writer.write(v);
    }
    writer.flush();
  }
}
