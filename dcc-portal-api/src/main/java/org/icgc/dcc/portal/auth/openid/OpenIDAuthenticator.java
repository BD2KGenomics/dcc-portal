/*
 * Copyright (c) 2013 The Ontario Institute for Cancer Research. All rights reserved.
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
package org.icgc.dcc.portal.auth.openid;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.portal.model.security.User;
import org.icgc.dcc.portal.service.DistributedCacheService;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;

/**
 * Authenticator which verifies if the provided OpenID credentials are valid.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @_({ @Inject }))
public class OpenIDAuthenticator implements Authenticator<OpenIDCredentials, User> {

  @NonNull
  private final DistributedCacheService cacheService;

  @Override
  public Optional<User> authenticate(OpenIDCredentials credentials) throws AuthenticationException {
    log.debug("Looking up user by session token '{}'...", credentials.getSessionToken());

    // Get the User referred to by the API key
    Optional<User> user = findUserBySessionToken(credentials);
    if (user.isPresent() && user.get().getDaco()) {
      return user;
    }

    return Optional.absent();
  }

  private Optional<User> findUserBySessionToken(OpenIDCredentials credentials) {
    return cacheService.getUserBySessionToken(credentials.getSessionToken());
  }

}
