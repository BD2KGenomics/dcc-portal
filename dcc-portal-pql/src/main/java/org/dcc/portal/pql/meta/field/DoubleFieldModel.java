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

import static org.dcc.portal.pql.meta.Constants.EMPTY_UI_ALIAS;
import static org.dcc.portal.pql.meta.Constants.NOT_NESTED;
import static org.dcc.portal.pql.meta.field.FieldModel.FieldType.DOUBLE;
import lombok.NonNull;

import org.dcc.portal.pql.meta.visitor.FieldVisitor;

public class DoubleFieldModel extends FieldModel {

  private DoubleFieldModel(String name) {
    this(name, EMPTY_UI_ALIAS);
  }

  private DoubleFieldModel(String name, String uiAlias) {
    this(name, uiAlias, NOT_NESTED);
  }

  private DoubleFieldModel(String name, boolean nested) {
    this(name, EMPTY_UI_ALIAS, nested);
  }

  private DoubleFieldModel(String name, String uiAlias, boolean nested) {
    super(name, uiAlias, DOUBLE, nested);
  }

  public static DoubleFieldModel double_(@NonNull String name) {
    return new DoubleFieldModel(name);
  }

  @Override
  public <T> T accept(FieldVisitor<T> visitor) {
    return visitor.visitDoubleField(this);
  }

}
