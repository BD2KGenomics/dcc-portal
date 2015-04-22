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

import static java.lang.String.format;
import lombok.Value;

@Value
public class IndexModel {

  private static final DonorCentricTypeModel DONOR_CENTRIC_TYPE_MODEL = new DonorCentricTypeModel();
  private static final GeneCentricTypeModel GENE_CENTRIC_TYPE_MODEL = new GeneCentricTypeModel();
  private static final MutationCentricTypeModel MUTATION_CENTRIC_TYPE_MODEL = new MutationCentricTypeModel();
  private static final ObservationCentricTypeModel OBSERVATION_CENTRIC_TYPE_MODEL = new ObservationCentricTypeModel();
  private static final ProjectTypeModel PROJECT_TYPE_MODEL = new ProjectTypeModel();

  public static TypeModel getTypeModel(Type type) {
    switch (type) {
    case DONOR_CENTRIC:
      return DONOR_CENTRIC_TYPE_MODEL;
    case GENE_CENTRIC:
      return GENE_CENTRIC_TYPE_MODEL;
    case MUTATION_CENTRIC:
      return MUTATION_CENTRIC_TYPE_MODEL;
    case OBSERVATION_CENTRIC:
      return OBSERVATION_CENTRIC_TYPE_MODEL;
    case PROJECT:
      return PROJECT_TYPE_MODEL;
    }

    throw new IllegalArgumentException(format("Type %s was not found", type.getId()));
  }

  public static TypeModel getDonorCentricTypeModel() {
    return DONOR_CENTRIC_TYPE_MODEL;
  }

  public static TypeModel getMutationCentricTypeModel() {
    return MUTATION_CENTRIC_TYPE_MODEL;
  }

  public static TypeModel getGeneCentricTypeModel() {
    return GENE_CENTRIC_TYPE_MODEL;
  }

  public static TypeModel getObservationCentricTypeModel() {
    return OBSERVATION_CENTRIC_TYPE_MODEL;
  }

  public static TypeModel getProjectTypeModel() {
    return PROJECT_TYPE_MODEL;
  }

}
