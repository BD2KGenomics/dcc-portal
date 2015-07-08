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

import static com.google.common.collect.Sets.difference;
import static java.lang.Boolean.FALSE;
import static java.lang.String.format;
import static org.icgc.dcc.portal.util.AuthUtils.throwForbiddenException;

import java.util.Set;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.common.collect.Sets;
import org.icgc.dcc.common.core.util.Splitters;
import org.icgc.dcc.portal.auth.oauth.OAuthClient;
import org.icgc.dcc.portal.model.AccessTokenScopes;
import org.icgc.dcc.portal.model.AccessTokenScopes.AccessTokenScope;
import org.icgc.dcc.portal.model.Tokens;
import org.icgc.dcc.portal.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableSet;

/**
 * OAuth access tokens management service.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TokenService {

  private static final String S3_DOWNLOAD_SCOPE_DESC = "Allows to download from the S3";
  private static final String S3_UPLOAD_SCOPE_DESC = "Allows to upload to the S3";
  private static final String S3_UPLOAD_SCOPE = "s3.upload";
  private static final String S3_DOWNLOAD_SCOPE = "s3.download";

  @NonNull
  private final OAuthClient client;

  public String create(User user, String scope, String description) {
    log.debug("Creating access token of scope '{}' for user '{}'...", scope, user);
    val userId = user.getEmailAddress();
    if (user.getDaco() == FALSE) {
      throwForbiddenException("The user is not DACO approved",
          format("User %s is not DACO approved to access the create access token resource", userId));
    }

    validateScope(user, scope);
    val token = client.createToken(userId, scope);
    log.debug("Created token '{}' for '{}'", token, userId);

    return token.getId();
  }

  public Tokens list(@NonNull User user) {
    return client.listTokens(user.getEmailAddress());
  }

  public void delete(@NonNull String tokenId) {
    client.revokeToken(tokenId);
  }

  public AccessTokenScopes userScopes(User user) {
    val userScopes = client.getUserScopes(user.getEmailAddress());
    val scopesResult = ImmutableSet.<AccessTokenScope> builder();

    for (val scope : userScopes.getScopes()) {
      switch (scope) {
      case S3_DOWNLOAD_SCOPE:
        scopesResult.add(new AccessTokenScope(S3_DOWNLOAD_SCOPE, S3_DOWNLOAD_SCOPE_DESC));
        break;
      case S3_UPLOAD_SCOPE:
        scopesResult.add(new AccessTokenScope(S3_DOWNLOAD_SCOPE, S3_UPLOAD_SCOPE_DESC));
        break;
      default:
        throw new RuntimeException(format("Unrecognized user scope: '%s'", scope));
      }
    }

    return new AccessTokenScopes(scopesResult.build());
  }

  private void validateScope(User user, String scope) {
    val requestScope = Sets.newHashSet(Splitters.WHITESPACE.split(scope));
    val userScopes = extractScopeNames(userScopes(user));

    // TODO: Create a method in common-core
    val scopeDiff = difference(requestScope, userScopes);
    if (!scopeDiff.isEmpty()) {
      throwForbiddenException("The user is not allowed to create tokens of this scope",
          format("User '%s' is not allowed to create tokens of scope '%s'.", user.getEmailAddress(), scope));
    }
  }

  private Set<String> extractScopeNames(AccessTokenScopes userScopes) {
    val result = ImmutableSet.<String> builder();
    for (val scope : userScopes.getScopes()) {
      result.add(scope.getName());
    }

    return result.build();
  }

}
