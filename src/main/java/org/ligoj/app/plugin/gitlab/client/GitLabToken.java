package org.ligoj.app.plugin.gitlab.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * The GitLab access token used by a request, see <code>GET /personal_access_tokens/self</code>.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabToken {

	/**
	 * Expiry day, <code>YYYY-MM-DD</code>. <code>null</code> for a token without expiry.
	 */
	@JsonProperty("expires_at")
	private String expiresAt;
}
