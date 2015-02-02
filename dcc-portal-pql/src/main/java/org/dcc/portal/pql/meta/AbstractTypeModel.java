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
package org.dcc.portal.pql.meta;

import static org.dcc.portal.pql.meta.Constants.FIELD_SEPARATOR;
import static org.icgc.dcc.common.core.util.FormatUtils._;

import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.assertj.core.util.Maps;
import org.dcc.portal.pql.meta.field.FieldModel;
import org.dcc.portal.pql.meta.visitor.CreateAliasVisitor;
import org.dcc.portal.pql.meta.visitor.CreateFullNameVisitor;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Slf4j
public abstract class AbstractTypeModel {

  protected final Map<String, FieldModel> fieldsByFullPath;
  protected final Map<String, String> fieldsByAlias;

  public AbstractTypeModel(List<? extends FieldModel> fields) {
    fieldsByFullPath = initFieldsByFullPath(fields);
    fieldsByAlias = initFieldsByAlias(fields);
  }

  private static Map<String, FieldModel> initFieldsByFullPath(List<? extends FieldModel> fields) {
    val result = Maps.<String, FieldModel> newHashMap();
    val visitor = new CreateFullNameVisitor();
    for (val field : fields) {
      result.putAll(field.accept(visitor));
    }

    return result;
  }

  private static Map<String, String> initFieldsByAlias(List<? extends FieldModel> fields) {
    val result = new ImmutableMap.Builder<String, String>();
    val visitor = new CreateAliasVisitor();
    for (val field : fields) {
      result.putAll(field.accept(visitor));
    }

    return result.build();
  }

  public final boolean isNested(String field) {
    val fullName = getFullName(field);
    val tokens = split(fullName);
    log.debug("Tokens: {}", tokens);
    for (val token : tokens) {
      val tokenByFullPath = fieldsByFullPath.get(token);
      if (tokenByFullPath.isNested()) {
        return true;
      }
    }

    return false;
  }

  private List<String> split(String fullName) {
    val result = new ImmutableList.Builder<String>();
    val list = Splitter.on(FIELD_SEPARATOR).splitToList(fullName);
    String prefix = "";
    for (int i = 0; i < list.size(); i++) {
      result.add(prefix + list.get(i));
      prefix = prefix + list.get(i) + FIELD_SEPARATOR;
    }

    return result.build().reverse();
  }

  public final String getNestedPath(@NonNull String field) {
    val fullName = getFullName(field);
    for (val token : split(fullName)) {
      val tokenByFullPath = fieldsByFullPath.get(token);
      if (tokenByFullPath.isNested()) {
        return token;
      }
    }

    throw new IllegalArgumentException("Can't get nested path for a non-nested field");
  }

  public final String getFullName(String path) {
    val uiAlias = fieldsByAlias.get(path);

    return uiAlias == null ? path : uiAlias;

  }

  @Override
  public String toString() {
    val buffer = new StringBuffer();
    val newLine = System.getProperty("line.separator");
    for (val entity : fieldsByFullPath.entrySet()) {
      val value = entity.getValue();
      buffer.append(_("Path: %s, Type: %s, Nested: %s", entity.getKey(), value.getType(), value.isNested()));
      buffer.append(newLine);
    }

    return buffer.toString();
  }

}
