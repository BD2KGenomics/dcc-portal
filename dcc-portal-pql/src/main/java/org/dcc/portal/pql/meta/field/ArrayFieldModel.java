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
package org.dcc.portal.pql.meta.field;

import static java.util.Collections.singleton;
import static org.dcc.portal.pql.meta.Constants.EMPTY_STRING_FIELD;
import static org.dcc.portal.pql.meta.Constants.EMPTY_UI_ALIAS;
import static org.dcc.portal.pql.meta.field.FieldModel.FieldType.ARRAY;

import java.util.Set;

import lombok.Getter;
import lombok.NonNull;

import org.dcc.portal.pql.meta.visitor.FieldVisitor;

@Getter
public class ArrayFieldModel extends FieldModel {

  private final FieldModel element;

  private ArrayFieldModel(String name, FieldModel element) {
    this(name, EMPTY_UI_ALIAS, element);
  }

  private ArrayFieldModel(String name, String alias, FieldModel element) {
    this(name, singleton(alias), element);
  }

  private ArrayFieldModel(String name, Set<String> aliases, FieldModel element) {
    this(name, aliases, FieldModel.NOT_NESTED, element);
  }

  private ArrayFieldModel(String name, boolean nested, FieldModel element) {
    this(name, EMPTY_UI_ALIAS, nested, element);
  }

  private ArrayFieldModel(String name, String alias, boolean nested, FieldModel element) {
    this(name, singleton(alias), nested, element);
  }

  private ArrayFieldModel(String name, Set<String> aliases, boolean nested, FieldModel element) {
    super(name, aliases, ARRAY, nested);
    this.element = element;
  }

  public static ArrayFieldModel arrayOfStrings(@NonNull String name) {
    return new ArrayFieldModel(name, EMPTY_STRING_FIELD);
  }

  public static ArrayFieldModel arrayOfStrings(@NonNull String name, @NonNull String alias) {
    return new ArrayFieldModel(name, alias, EMPTY_STRING_FIELD);
  }

  public static ArrayFieldModel arrayOfStrings(@NonNull String name, @NonNull Set<String> aliases) {
    return new ArrayFieldModel(name, aliases, EMPTY_STRING_FIELD);
  }

  public static ArrayFieldModel nestedArrayOfStrings(@NonNull String name, @NonNull Set<String> alias) {
    return new ArrayFieldModel(name, alias, FieldModel.NESTED, EMPTY_STRING_FIELD);
  }

  public static ArrayFieldModel nestedArrayOfObjects(@NonNull String name, @NonNull ObjectFieldModel element) {
    return new ArrayFieldModel(name, FieldModel.NESTED, element);
  }

  @Override
  public <T> T accept(@NonNull FieldVisitor<T> visitor) {
    return visitor.visitArrayField(this);
  }

}
